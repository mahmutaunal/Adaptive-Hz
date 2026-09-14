package com.mahmutalperenunal.adaptivehz.core.shizuku

/**
 * Owns one temporary HyperOS secure-setting override and restores the exact previous value.
 *
 * The lease deliberately refuses to switch keys. Candidate probing must first release the
 * current lease, which prevents one failed probe from orphaning another vendor setting.
 */
internal class HyperOsRefreshRateSettingLease(
    private val managedKeys: List<String>,
    private val read: (String) -> SettingState?,
    private val write: (String, String?) -> Boolean
) {
    data class SettingState(val value: String?)

    private data class Snapshot(
        val key: String,
        val originals: Map<String, SettingState>
    )

    private var snapshot: Snapshot? = null

    @Synchronized
    fun apply(key: String, refreshRate: Int): Boolean {
        if (refreshRate <= 0) return false

        val active = snapshot
        if (active != null && active.key != key) return false

        val captured = active ?: run {
            val originals = managedKeys.associateWith { managedKey ->
                read(managedKey) ?: return false
            }
            Snapshot(key = key, originals = originals).also { snapshot = it }
        }

        if (!write(captured.key, refreshRate.toString())) {
            restoreOriginal()
            return false
        }

        val verified = read(captured.key)?.value?.toIntOrNull() == refreshRate
        if (!verified) restoreOriginal()
        return verified
    }

    @Synchronized
    fun restoreOriginal(): Boolean {
        val active = snapshot ?: return true
        repeat(RESTORE_ATTEMPTS) {
            val writesSucceeded = active.originals.map { (key, original) ->
                write(key, original.value)
            }.all { it }
            val restored = writesSucceeded && active.originals.all { (key, original) ->
                read(key)?.value == original.value
            }
            if (restored) {
                snapshot = null
                return true
            }
        }
        return false
    }

    private companion object {
        const val RESTORE_ATTEMPTS = 3
    }
}
