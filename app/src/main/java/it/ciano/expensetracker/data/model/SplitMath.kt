package it.ciano.expensetracker.data.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Matematica di divisione spese — pura, testabile su JVM, nessuna dipendenza Android.
 *
 * Regole (convenzione repo: money = Double, arrotondamento per-task):
 * - [share] : quota a persona = round(total / n, 2) con HALF_EVEN — usata in modalità A
 *   ("ognuno paga il suo": la mia parte è [share]) e come quota di ciascun altro in B1.
 * - [myShare] : in B1 la MIA parte assorbe il resto così che
 *   shares(n-1) + myShare == total ESATTAMENTE — zero drift centesimi.
 */
object SplitMath {

    private fun round2Cents(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_EVEN).toDouble()

    /** Quota esatta a persona (modalità A / quota di ciascun altro in B1). */
    fun share(total: Double, n: Int): Double {
        require(n >= 2) { "split richiede almeno 2 persone" }
        return round2Cents(total / n)
    }

    /** Lista delle n-1 quote degli ALTRI in B1 (tutte uguali a [share]). */
    fun shares(total: Double, n: Int): List<Double> {
        require(n >= 2) { "split richiede almeno 2 persone" }
        return List(n - 1) { share(total, n) }
    }

    /** La MIA quota in B1: assorbe il resto perché la somma torni esatta. */
    fun myShare(total: Double, n: Int): Double {
        require(n >= 2) { "split richiede almeno 2 persone" }
        val othersSum = round2Cents(share(total, n) * (n - 1))
        return round2Cents(total - othersSum)
    }
}
