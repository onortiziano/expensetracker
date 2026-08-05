package it.ciano.expensetracker

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import it.ciano.expensetracker.ui.theme.ExpenseTrackerTheme
import it.ciano.expensetracker.ui.screens.AppNavigation

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val code = LocaleHelper.getSavedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, code))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        val code = LocaleHelper.getSavedLanguage(this)
        if (code != LocaleHelper.LANGUAGE_SYSTEM) {
            val locale = java.util.Locale(code)
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            super.applyOverrideConfiguration(config)
        } else {
            super.applyOverrideConfiguration(overrideConfiguration)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
