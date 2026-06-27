package it.ciano.expensetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_DECIMAL_SEPARATOR = "decimal_separator"
    }

    fun saveCurrency(symbol: String) {
        sharedPreferences.edit().putString(KEY_CURRENCY, symbol).apply()
    }

    fun getCurrency(): String {
        return sharedPreferences.getString(KEY_CURRENCY, "€") ?: "€"
    }

    fun saveDecimalSeparator(separator: String) {
        sharedPreferences.edit().putString(KEY_DECIMAL_SEPARATOR, separator).apply()
    }

    fun getDecimalSeparator(): String {
        return sharedPreferences.getString(KEY_DECIMAL_SEPARATOR, ",") ?: ","
    }
}