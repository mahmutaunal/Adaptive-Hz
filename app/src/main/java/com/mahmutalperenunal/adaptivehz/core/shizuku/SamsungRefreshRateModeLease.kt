package com.mahmutalperenunal.adaptivehz.core.shizuku

/**
 * Temporarily opens Samsung's full physical refresh-rate range for a custom token session.
 *
 * The setting value that was active before the first custom token is retained across token
 * replacements and restored only when the complete custom session ends.
 */
internal class SamsungRefreshRateModeLease(
    private val readMode: () -> Int?,
    private val writeMode: (Int) -> Boolean
) {
    private var originalMode: Int? = null

    fun prepareHighRange(): Boolean {
        val currentMode = readMode()?.takeIf { it in SUPPORTED_MODES } ?: return false
        val capturedOriginal = originalMode
        if (capturedOriginal == null) originalMode = currentMode

        if (currentMode == HIGH_MODE) return true

        val prepared = writeMode(HIGH_MODE) && readMode() == HIGH_MODE
        if (!prepared && capturedOriginal == null) originalMode = null
        return prepared
    }

    fun restoreOriginal(): Boolean {
        val targetMode = originalMode ?: return true
        val currentMode = readMode()
        val restored = currentMode == targetMode ||
            (writeMode(targetMode) && readMode() == targetMode)

        if (restored) originalMode = null
        return restored
    }

    companion object {
        private const val HIGH_MODE = 2
        private val SUPPORTED_MODES = 0..2
    }
}
