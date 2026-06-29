package it.ciano.expensetracker.data.preferences

import android.content.Context
import android.content.SharedPreferences
import java.util.Currency
import java.util.Locale

class UserPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CURRENCY = "currency_symbol"
        private const val KEY_DECIMAL_SEPARATOR = "decimal_separator"
    }

    fun saveCurrency(symbol: String) {
        sharedPreferences.edit().putString(KEY_CURRENCY, symbol).commit()
    }

    fun getCurrency(): String {
        // 1. Prova a leggere la preferenza salvata dall'utente
        val savedCurrency = sharedPreferences.getString(KEY_CURRENCY, null)
        if (savedCurrency != null) return savedCurrency

        // 2. Se non c'è, recupera la valuta di sistema basata sul Locale
        return try {
            Currency.getInstance(Locale.getDefault().country).symbol
        } catch (e: Exception) {
            "€" // Fallback finale se il Locale non ha una valuta definita
        }
    }

    fun saveDecimalSeparator(separator: String) {
        sharedPreferences.edit().putString(KEY_DECIMAL_SEPARATOR, separator).commit()
    }

    fun getDecimalSeparator(): String {
        // 1. Prova a leggere la preferenza salvata dall'utente
        val savedSeparator = sharedPreferences.getString(KEY_DECIMAL_SEPARATOR, null)
        if (savedSeparator != null) return savedSeparator

        // 2. Se non c'è, recupera il separatore decimale di sistema
        return java.text.DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator.toString()
    }
}
