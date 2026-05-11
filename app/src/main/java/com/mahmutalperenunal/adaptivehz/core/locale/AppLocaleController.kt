package com.mahmutalperenunal.adaptivehz.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.mahmutalperenunal.adaptivehz.core.prefs.AppLanguage

/**
 * Applies the app-specific locale using AndroidX per-app language APIs.
 *
 * This keeps the selected language persisted and synchronized with the
 * current application configuration across process restarts.
 */
object AppLocaleController {

    fun apply(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.TR -> LocaleListCompat.forLanguageTags("tr")
            AppLanguage.ES -> LocaleListCompat.forLanguageTags("es")
        }

        AppCompatDelegate.setApplicationLocales(locales)
    }
}