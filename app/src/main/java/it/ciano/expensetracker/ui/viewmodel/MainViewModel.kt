package it.ciano.expensetracker.ui.viewmodel

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Filled
import androidx.compose.material.icons.Icons.Outlined
import androidx.compose.material.icons.Icons.Rounded
import androidx.compose.material.icons.Icons.Sharp
import androidx.compose.material.icons.Icons.TwoTone
import androidx.compose.ui.graphics.vector.ImageVector
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

    // 3. Stato dello Stile Icone
    private val _iconStyle = MutableStateFlow(userPreferences.getIconStyle())
    val iconStyle: StateFlow<String> = _iconStyle.asStateFlow()

    // Funzione per cambiare il tema
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    // Funzione per cambiare lo stile delle icone
    fun setIconStyle(style: String) {
        _iconStyle.value = style
    }

    /**
     * Fornisce l'icona corretta in base allo stile scelto dall'utente.
     * Se l'icona non è disponibile nello stile richiesto, torna a FILLED di default.
     */
    fun getIcon(
        filled: ImageVector,
        outlined: ImageVector? = null,
        rounded: ImageVector? = null,
        sharp: ImageVector? = null,
        twoTone: ImageVector? = null
    ): ImageVector {
        return when (_iconStyle.value) {
            "OUTLINED" -> outlined ?: filled
            "ROUNDED" -> rounded ?: filled
            "SHARP" -> sharp ?: filled
            "TWO_TONE" -> twoTone ?: filled
            else -> filled
        }
    }

    // Funzione per formattare l'importo in base alla valuta e al separatore scelti
    fun formatCurrency(amount: Double): String {
        val symbol = userPreferences.getCurrency()
        val separator = userPreferences.getDecimalSeparator()
        
        // Creiamo i simboli di formattazione basandoci sul locale di sistema
        val symbols = java.text.DecimalFormatSymbols(java.util.Locale.getDefault())
        // Sovrascriviamo solo il separatore decimale con quello scelto dall'utente
        symbols.decimalSeparator = separator[0]
        
        // Creiamo il formatore usando i nostri simboli personalizzati
        val df = java.text.DecimalFormat("0.00", symbols)
        
        return "${df.format(amount)} $symbol"
    }
}
