package com.mahmutalperenunal.adaptivehz.core.engine.model

/** Effective behavior after applying global-off and per-app precedence rules. */
internal enum class EffectiveRefreshPolicy {
    SYSTEM_CONTROLLED,
    ADAPTIVE,
    FORCE_MINIMUM,
    FORCE_MAXIMUM
}

internal object EffectiveRefreshPolicyResolver {
    fun resolve(
        globalMode: AdaptiveHzMode,
        appMode: AppRefreshProfileMode
    ): EffectiveRefreshPolicy {
        if (globalMode == AdaptiveHzMode.OFF) {
            return EffectiveRefreshPolicy.SYSTEM_CONTROLLED
        }

        return when (appMode) {
            AppRefreshProfileMode.SYSTEM_CONTROLLED -> EffectiveRefreshPolicy.SYSTEM_CONTROLLED
            AppRefreshProfileMode.FORCE_MIN -> EffectiveRefreshPolicy.FORCE_MINIMUM
            AppRefreshProfileMode.FORCE_MAX -> EffectiveRefreshPolicy.FORCE_MAXIMUM
            AppRefreshProfileMode.DEFAULT -> when (globalMode) {
                AdaptiveHzMode.OFF -> EffectiveRefreshPolicy.SYSTEM_CONTROLLED
                AdaptiveHzMode.ADAPTIVE -> EffectiveRefreshPolicy.ADAPTIVE
                AdaptiveHzMode.FORCE_MIN -> EffectiveRefreshPolicy.FORCE_MINIMUM
                AdaptiveHzMode.FORCE_MAX -> EffectiveRefreshPolicy.FORCE_MAXIMUM
            }
        }
    }
}
