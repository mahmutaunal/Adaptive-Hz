package com.mahmutalperenunal.adaptivehz.core.system

/** Pure selection rules for HyperOS 3 custom-rate transport discovery. */
internal object HyperOsCustomRefreshRateContract {
    const val USER_REFRESH_RATE = "user_refresh_rate"
    const val MIUI_REFRESH_RATE = "miui_refresh_rate"

    val allowedKeys = setOf(USER_REFRESH_RATE, MIUI_REFRESH_RATE)

    data class Candidate(val key: String, val currentValue: Int?)

    /**
     * A value matching the real active mode is the strongest observed signal. HyperOS 3's
     * nominal miui key is only a tie-breaker; real ROM state always wins over the key name.
     */
    fun orderCandidates(
        candidates: List<Candidate>,
        activeRefreshRate: Int,
        activeLeaseKey: String? = null
    ): List<Candidate> {
        return candidates
            .filter { it.key in allowedKeys }
            .distinctBy { it.key }
            .sortedWith(
                compareBy<Candidate> {
                    when {
                        it.key == activeLeaseKey -> 0
                        it.currentValue == activeRefreshRate -> 1
                        it.key == MIUI_REFRESH_RATE -> 2
                        it.currentValue != null -> 3
                        else -> 4
                    }
                }.thenBy { it.key }
            )
    }
}
