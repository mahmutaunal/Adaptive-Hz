package com.mahmutalperenunal.adaptivehz.core.shizuku

import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method

/**
 * Owns Samsung refresh-rate limit tokens inside the Shizuku UserService process.
 *
 * The owner Binder and returned token objects live only in this process. DisplayManagerService can
 * therefore remove the limits when this process dies, even if explicit cleanup cannot run.
 */
internal class SamsungRefreshRateTokenController {

    private val lock = Any()
    private val ownerToken: IBinder = Binder()

    private val lease = SessionRefreshRateTokenLease<Any>()

    @Volatile
    private var resolvedApi: ResolvedApi? = null

    fun inspectApi(): String {
        return runCatching {
            val api = resolveApi()
            buildString {
                appendLine("status=API_CANDIDATE")
                appendLine("lifetime=PROCESS_BINDER")
                appendLine("min=${api.minAcquire.toGenericString()}")
                appendLine("max=${api.maxAcquire.toGenericString()}")
                append("release=${api.release.toGenericString()}")
            }
        }.getOrElse { error ->
            "status=UNAVAILABLE\nreason=${error.javaClass.simpleName}:${error.message.orEmpty()}"
        }
    }

    fun applyLimits(minRefreshRate: Int, maxRefreshRate: Int): Boolean {
        if (!isValidRange(minRefreshRate, maxRefreshRate)) return false

        return synchronized(lock) {
            if (lease.isActiveFor(minRefreshRate, maxRefreshRate)) {
                return@synchronized true
            }

            runCatching {
                val api = resolveApi()
                if (!releaseLimitsLocked(api.release)) {
                    error("Existing Samsung refresh-rate tokens could not be released")
                }

                val newMinToken = api.minAcquire.invoke(
                    api.displayManager,
                    ownerToken,
                    minRefreshRate,
                    TOKEN_TAG
                ) ?: error("Samsung min-limit API returned a null token")

                val newMaxToken = try {
                    api.maxAcquire.invoke(
                        api.displayManager,
                        ownerToken,
                        maxRefreshRate,
                        TOKEN_TAG
                    ) ?: error("Samsung max-limit API returned a null token")
                } catch (error: Throwable) {
                    if (!releaseToken(api.release, newMinToken)) {
                        // Keep a strong reference so cleanup can be retried while the owner Binder
                        // is alive. Process death remains the final cleanup guarantee.
                        lease.retainPartiallyAcquiredMin(newMinToken)
                    }
                    throw error
                }

                lease.activate(newMinToken, newMaxToken, minRefreshRate, maxRefreshRate)
                true
            }.onFailure { error ->
                Log.w(TAG, "Samsung refresh-rate tokens were not acquired", error)
            }.getOrDefault(false)
        }
    }

    fun releaseLimits(): Boolean {
        return synchronized(lock) {
            if (!lease.hasTokens()) return@synchronized true

            val releaseMethod = runCatching { resolveApi().release }
                .onFailure { Log.w(TAG, "Unable to resolve Samsung token release API", it) }
                .getOrNull()
                ?: return@synchronized false

            releaseLimitsLocked(releaseMethod)
        }
    }

    private fun resolveApi(): ResolvedApi {
        resolvedApi?.let { return it }

        val displayManager = resolveDisplayManager()
        // Prefer the public Binder interface methods over methods declared by its private Proxy.
        // Invoking a Method whose declaring Proxy class is private can fail with IllegalAccessException.
        val methods = displayManager.javaClass.interfaces.asSequence()
            .flatMap { it.methods.asSequence() } + displayManager.javaClass.methods.asSequence()

        // A generated Binder proxy and its interface can expose the same method twice. Collapse
        // those views by callable signature so a valid Samsung API is not rejected as ambiguous.
        val allMethods = methods.distinctBy { method -> method.callableSignature() }.toList()
        val minAcquire = allMethods.singleSupportedAcquire(SamsungRefreshRateTokenContract.MIN_METHOD)
        val maxAcquire = allMethods.singleSupportedAcquire(SamsungRefreshRateTokenContract.MAX_METHOD)

        val tokenClass = Class.forName(REFRESH_RATE_TOKEN_CLASS)
        val release = tokenClass.methods.singleOrNull { method ->
            SamsungRefreshRateTokenContract.isSupportedReleaseMethod(
                name = method.name,
                parameterTypeNames = method.parameterTypes.map(Class<*>::getName),
                returnTypeName = method.returnType.name
            )
        } ?: error("A unique IRefreshRateToken.release() method was not found")

        return ResolvedApi(displayManager, minAcquire, maxAcquire, release).also {
            resolvedApi = it
        }
    }

    private fun resolveDisplayManager(): Any {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getService = serviceManagerClass.getDeclaredMethod("getService", String::class.java)
        val displayBinder = getService.invoke(null, DISPLAY_SERVICE) as? IBinder
            ?: error("DisplayManagerService binder is unavailable")

        val stubClass = Class.forName("android.hardware.display.IDisplayManager\$Stub")
        val asInterface = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, displayBinder)
            ?: error("IDisplayManager proxy is unavailable")
    }

    private fun List<Method>.singleSupportedAcquire(name: String): Method {
        return singleOrNull { method ->
            method.name == name && SamsungRefreshRateTokenContract.isSupportedAcquireMethod(
                name = method.name,
                parameterTypeNames = method.parameterTypes.map(Class<*>::getName),
                returnTypeName = method.returnType.name
            )
        } ?: error("A unique supported $name method was not found")
    }

    private fun Method.callableSignature(): String {
        return buildString {
            append(name)
            append('(')
            append(parameterTypes.joinToString(separator = ",") { it.name })
            append("):")
            append(returnType.name)
        }
    }

    private fun releaseToken(releaseMethod: Method, token: Any?): Boolean {
        if (token == null) return true
        return runCatching {
            releaseMethod.invoke(token)
            true
        }.onFailure { error ->
            Log.w(TAG, "Samsung refresh-rate token release failed", error)
        }.getOrDefault(false)
    }

    private fun releaseLimitsLocked(releaseMethod: Method): Boolean {
        return lease.releaseAll { token -> releaseToken(releaseMethod, token) }
    }

    private fun isValidRange(minRefreshRate: Int, maxRefreshRate: Int): Boolean {
        return minRefreshRate in MIN_REFRESH_RATE..MAX_REFRESH_RATE &&
            maxRefreshRate in MIN_REFRESH_RATE..MAX_REFRESH_RATE &&
            minRefreshRate <= maxRefreshRate
    }

    private data class ResolvedApi(
        val displayManager: Any,
        val minAcquire: Method,
        val maxAcquire: Method,
        val release: Method
    )

    companion object {
        private const val TAG = "SamsungRateTokens"
        private const val DISPLAY_SERVICE = "display"
        private const val REFRESH_RATE_TOKEN_CLASS =
            "com.samsung.android.hardware.display.IRefreshRateToken"
        private const val TOKEN_TAG = "AdaptiveHz"
        private const val MIN_REFRESH_RATE = 1
        private const val MAX_REFRESH_RATE = 1_000
    }
}
