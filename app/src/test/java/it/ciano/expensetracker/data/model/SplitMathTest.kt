package it.ciano.expensetracker.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitMathTest {

    // --- A: ognuno paga il suo → solo la MIA parte, quota a persona ---

    @Test
    fun `A 83 in 3 → 27,67`() {
        assertEquals(27.67, SplitMath.share(83.0, 3), 0.0001)
    }

    @Test
    fun `A 100 in 4 → 25,00`() {
        assertEquals(25.0, SplitMath.share(100.0, 4), 0.0001)
    }

    @Test
    fun `A 83 in 2 → 41,50`() {
        assertEquals(41.50, SplitMath.share(83.0, 2), 0.0001)
    }

    // --- B1: pago io per tutti → quota di CIOASCun ALTRO + la mia che assorbe il resto (zero drift) ---

    @Test
    fun `B1 83 in 3 → altri 27,67 x2 + mia 27,66, somma == 83`() {
        val others = SplitMath.shares(83.0, 3)          // [27,67, 27,67]
        val myPart = SplitMath.myShare(83.0, 3)          // 27,66 (assorbe il resto)
        assertEquals(27.67, others[0], 0.0001)
        assertEquals(27.67, others[1], 0.0001)
        assertEquals(27.66, myPart, 0.0001)
        assertEquals(83.0, others.sum() + myPart, 0.0001)   // zero drift
    }

    @Test
    fun `B1 83 in 2 → altro 41,50 + mia 41,50, somma == 83`() {
        val others = SplitMath.shares(83.0, 2)          // [41,50]
        assertEquals(41.50, others[0], 0.0001)
        assertEquals(41.50, SplitMath.myShare(83.0, 2), 0.0001)
    }

    @Test
    fun `B1 10 in 3 → altri 3,33 x2 + mia 3,34, somma == 10`() {
        val others = SplitMath.shares(10.0, 3)          // [3,33, 3,33]
        assertEquals(3.33, others[0], 0.0001)
        assertEquals(3.33, others[1], 0.0001)
        assertEquals(3.34, SplitMath.myShare(10.0, 3), 0.0001)
        assertEquals(10.0, others.sum() + SplitMath.myShare(10.0, 3), 0.0001)
    }

    @Test
    fun `B1 10 in 2 → 5,00 ciascuno, somma == 10`() {
        val others = SplitMath.shares(10.0, 2)
        assertEquals(5.00, others[0], 0.0001)
        assertEquals(5.00, SplitMath.myShare(10.0, 2), 0.0001)
    }

    // --- casi limite / invarianti ---

    @Test(expected = IllegalArgumentException::class)
    fun `n 1 → illegale (serve almeno 2 per dividere)`() {
        SplitMath.share(10.0, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `n 0 → illegale`() {
        SplitMath.share(10.0, 0)
    }

    @Test
    fun `totale 0 → 0 per tutti`() {
        assertEquals(0.0, SplitMath.share(0.0, 2), 0.0001)
        assertEquals(0.0, SplitMath.myShare(0.0, 2), 0.0001)
    }

    @Test
    fun `valori non arrotondabili → niente sovra o sottra drastico`() {
        var sum = 0.0
        for (i in 11..200) {
            val others = SplitMath.shares(i * 1.0, i / 2 + 1)
            sum += others.sum() + SplitMath.myShare(i * 1.0, i / 2 + 1)
        }
        assertEquals(0.0, (sum - (11..200).sum()), 5.0)  // drift complessivo ~0
    }
}
