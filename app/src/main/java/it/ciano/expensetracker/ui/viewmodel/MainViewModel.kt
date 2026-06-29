package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import it.ciano.expensetracker.data.preferences.UserPreferences

// Definiamo i possibili stati del tema
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    // 1. Stato del Tema: Iniziamo con SYSTEM (segue l'impostazione del telefono)
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // 2. Stato della Valuta: Leggiamo il valore salvato nelle preferenze
    private val _currency = MutableStateFlow(userPreferences.getCurrency())
    val currency: StateFlow<String> = _currency.asStateFlow()

    // Funzione per cambiare il tema
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    // Funzione per formattare l'importo in base alla valuta e al separatore scelti
    fun formatCurrency(amount: Double): String {
        val symbol = userPreferences.getCurrency()
        val separator = userPreferences.getDecimalSeparator()
        
        // Formattiamo a 2 decimali (usa il punto di default)
        val formatted = "%.2f".format(amount)
        
        // Sostituiamo il punto con il separatore scelto dall'utente
        val localized = formatted.replace(".", separator)
        
        return "$localized $symbol"
    }