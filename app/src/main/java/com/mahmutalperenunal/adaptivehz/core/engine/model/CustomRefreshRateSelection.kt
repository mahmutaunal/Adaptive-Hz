package com.mahmutalperenunal.adaptivehz.core.engine.model

internal data class CustomRateSelectionState(
    val globalMode: AdaptiveHzMode,
    val appMode: AppRefreshProfileMode = AppRefreshProfileMode.DEFAULT,
    val globalMinimum: Int? = null,
    val globalMaximum: Int? = null,
    val globalAdaptiveTarget: Int? = null,
    val appMinimum: Int? = null,
    val appMaximum: Int? = null
)

internal object CustomRateSelectionResolver {
    fun low(state: CustomRateSelectionState): Int? = when {
        state.appMode == AppRefreshProfileMode.FORCE_MIN -> state.appMinimum
        state.appMode != AppRefreshProfileMode.DEFAULT -> null
        state.globalMode == AdaptiveHzMode.ADAPTIVE -> null
        state.globalMode == AdaptiveHzMode.FORCE_MIN -> state.globalMinimum
        else -> null
    }

    fun high(state: CustomRateSelectionState): Int? = when {
        state.appMode == AppRefreshProfileMode.DEFAULT &&
            state.globalMode == AdaptiveHzMode.ADAPTIVE -> state.globalAdaptiveTarget
        else -> null
    }

    fun forceMinimum(state: CustomRateSelectionState): Int? = when {
        state.appMode == AppRefreshProfileMode.FORCE_MIN -> state.appMinimum
        state.appMode != AppRefreshProfileMode.DEFAULT -> null
        state.globalMode == AdaptiveHzMode.FORCE_MIN -> state.globalMinimum
        else -> null
    }

    fun forceMaximum(state: CustomRateSelectionState): Int? = when {
        state.appMode == AppRefreshProfileMode.FORCE_MAX -> state.appMaximum
        state.appMode != AppRefreshProfileMode.DEFAULT -> null
        state.globalMode == AdaptiveHzMode.FORCE_MAX -> state.globalMaximum
        else -> null
    }
}
