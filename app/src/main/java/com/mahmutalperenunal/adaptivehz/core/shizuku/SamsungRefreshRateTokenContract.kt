package com.mahmutalperenunal.adaptivehz.core.shizuku

/**
 * Strict contract for Samsung's private, session-scoped refresh-rate token API.
 *
 * This intentionally accepts only the signature observed in Samsung framework builds. Unknown
 * overloads are rejected instead of guessing how to call a private API.
 */
internal object SamsungRefreshRateTokenContract {

    const val MIN_METHOD = "acquireRefreshRateMinLimitToken"
    const val MAX_METHOD = "acquireRefreshRateMaxLimitToken"
    const val RELEASE_METHOD = "release"

    private val acquireParameterTypes = listOf(
        "android.os.IBinder",
        "int",
        "java.lang.String"
    )

    fun isSupportedAcquireMethod(
        name: String,
        parameterTypeNames: List<String>,
        returnTypeName: String
    ): Boolean {
        return name in setOf(MIN_METHOD, MAX_METHOD) &&
            parameterTypeNames == acquireParameterTypes &&
            returnTypeName == "com.samsung.android.hardware.display.IRefreshRateToken"
    }

    fun isSupportedReleaseMethod(
        name: String,
        parameterTypeNames: List<String>,
        returnTypeName: String
    ): Boolean {
        return name == RELEASE_METHOD &&
            parameterTypeNames.isEmpty() &&
            returnTypeName == "void"
    }

    fun isCandidateReport(report: String?): Boolean {
        return report
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim() == "status=API_CANDIDATE"
    }
}
