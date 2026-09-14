package com.mahmutalperenunal.adaptivehz.core.shizuku

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungRefreshRateTokenContractTest {

    private val expectedParameters = listOf(
        "android.os.IBinder",
        "int",
        "java.lang.String"
    )

    @Test
    fun acceptsObservedSamsungAcquireContract() {
        assertTrue(
            SamsungRefreshRateTokenContract.isSupportedAcquireMethod(
                name = SamsungRefreshRateTokenContract.MIN_METHOD,
                parameterTypeNames = expectedParameters,
                returnTypeName = "com.samsung.android.hardware.display.IRefreshRateToken"
            )
        )
        assertTrue(
            SamsungRefreshRateTokenContract.isSupportedAcquireMethod(
                name = SamsungRefreshRateTokenContract.MAX_METHOD,
                parameterTypeNames = expectedParameters,
                returnTypeName = "com.samsung.android.hardware.display.IRefreshRateToken"
            )
        )
    }

    @Test
    fun rejectsUnknownOverloadInsteadOfGuessing() {
        assertFalse(
            SamsungRefreshRateTokenContract.isSupportedAcquireMethod(
                name = SamsungRefreshRateTokenContract.MAX_METHOD,
                parameterTypeNames = expectedParameters + "int",
                returnTypeName = "com.samsung.android.hardware.display.IRefreshRateToken"
            )
        )
    }

    @Test
    fun rejectsWrongReturnType() {
        assertFalse(
            SamsungRefreshRateTokenContract.isSupportedAcquireMethod(
                name = SamsungRefreshRateTokenContract.MIN_METHOD,
                parameterTypeNames = expectedParameters,
                returnTypeName = "android.os.IBinder"
            )
        )
    }

    @Test
    fun acceptsOnlyNoArgumentVoidRelease() {
        assertTrue(
            SamsungRefreshRateTokenContract.isSupportedReleaseMethod(
                name = "release",
                parameterTypeNames = emptyList(),
                returnTypeName = "void"
            )
        )
        assertFalse(
            SamsungRefreshRateTokenContract.isSupportedReleaseMethod(
                name = "release",
                parameterTypeNames = listOf("boolean"),
                returnTypeName = "void"
            )
        )
    }

    @Test
    fun acceptsOnlyExactCandidateStatusHeader() {
        assertTrue(
            SamsungRefreshRateTokenContract.isCandidateReport(
                "status=API_CANDIDATE\nlifetime=PROCESS_BINDER"
            )
        )
        assertFalse(SamsungRefreshRateTokenContract.isCandidateReport("status=UNAVAILABLE"))
        assertFalse(SamsungRefreshRateTokenContract.isCandidateReport(null))
    }
}
