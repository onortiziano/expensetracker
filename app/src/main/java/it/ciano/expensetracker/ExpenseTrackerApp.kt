package it.ciano.expensetracker

import android.app.Application
import android.content.Context
import it.ciano.expensetracker.data.preferences.UserPreferences

class ExpenseTrackerApp : Application() {

    override fun attachBaseContext(base: Context) {
        val code = UserPreferences(base).getAppLanguage()
        super.attachBaseContext(LocaleHelper.wrap(base, code))
    }
}
