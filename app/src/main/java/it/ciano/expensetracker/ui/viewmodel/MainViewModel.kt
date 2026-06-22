package it.ciano.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Definiamo i possibili stati del tema
enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

class MainViewModel : ViewModel() {

    // 1. Stato del Tema: Iniziamo con SYSTEM (segue l'impostazione del telefono)
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // 2. Stato della Valuta: Iniziamo con l'Euro
    private val _currency = MutableStateFlow("€")
    val currency: StateFlow<String> = _currency.asStateFlow()

    // Funzione per cambiare il tema
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    // Funzione per cambiare la valuta
    fun setCurrency(newCurrency: String) {
        _currency.value = newCurrency
    }
}