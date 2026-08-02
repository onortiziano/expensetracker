package it.ciano.expensetracker

import android.content.Context
import android.content.res.Configuration
import it.ciano.expensetracker.data.preferences.UserPreferences
import java.util.Locale

object LocaleHelper {

    const val LANGUAGE_SYSTEM = "system"
    const val LANGUAGE_IT = "it"
    const val LANGUAGE_EN = "en"

    /**
     * Crea un context "avvolto" (wrapped) con la lingua scelta.
     * Su HyperOS le API di sistema vengono ignorate, quindi forziamo
     * il locale direttamente nelle Resources del context.
     */
    fun wrap(context: Context, languageCode: String): Context {
        if (languageCode == LANGUAGE_SYSTEM) return context
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun getSavedLanguage(context: Context): String {
        return UserPreferences(context).getAppLanguage()
    }
}
