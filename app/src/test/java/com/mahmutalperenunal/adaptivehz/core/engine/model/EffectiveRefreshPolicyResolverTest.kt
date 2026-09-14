package com.mahmutalperenunal.adaptivehz.core.engine.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveRefreshPolicyResolverTest {
    @Test
    fun `global off always restores system behavior`() {
        AppRefreshProfileMode.entries.forEach { appMode ->
            assertEquals(
                EffectiveRefreshPolicy.SYSTEM_CONTROLLED,
                EffectiveRefreshPolicyResolver.resolve(AdaptiveHzMode.OFF, appMode)
            )
        }
    }

    @Test
    fun `explicit app profiles override every enabled global mode`() {
        val enabledGlobalModes = AdaptiveHzMode.entries.filterNot { it == AdaptiveHzMode.OFF }

        enabledGlobalModes.forEach { globalMode ->
            assertEquals(
                EffectiveRefreshPolicy.SYSTEM_CONTROLLED,
                EffectiveRefreshPolicyResolver.resolve(
                    globalMode,
                    AppRefreshProfileMode.SYSTEM_CONTROLLED
                )
            )
            assertEquals(
                EffectiveRefreshPolicy.FORCE_MINIMUM,
                EffectiveRefreshPolicyResolver.resolve(globalMode, AppRefreshProfileMode.FORCE_MIN)
            )
            assertEquals(
                EffectiveRefreshPolicy.FORCE_MAXIMUM,
                EffectiveRefreshPolicyResolver.resolve(globalMode, AppRefreshProfileMode.FORCE_MAX)
            )
        }
    }

    @Test
    fun `default app follows each enabled global mode`() {
        assertEquals(
            EffectiveRefreshPolicy.ADAPTIVE,
            EffectiveRefreshPolicyResolver.resolve(
                AdaptiveHzMode.ADAPTIVE,
                AppRefreshProfileMode.DEFAULT
            )
        )
        assertEquals(
            EffectiveRefreshPolicy.FORCE_MINIMUM,
            EffectiveRefreshPolicyResolver.resolve(
                AdaptiveHzMode.FORCE_MIN,
                AppRefreshProfileMode.DEFAULT
            )
        )
        assertEquals(
            EffectiveRefreshPolicy.FORCE_MAXIMUM,
            EffectiveRefreshPolicyResolver.resolve(
                AdaptiveHzMode.FORCE_MAX,
                AppRefreshProfileMode.DEFAULT
            )
        )
    }
}
