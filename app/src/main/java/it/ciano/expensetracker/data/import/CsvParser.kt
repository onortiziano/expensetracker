package it.ciano.expensetracker.data.import

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parser CSV hand-written (RFC 4180 essenziale: quote doppie, delimitatori
 * interni, campi su più righe). Nessuna dipendenza esterna.
 */
object CsvParser {

    private enum class Role { DATE, DESCRIPTION, AMOUNT, TYPE, CATEGORY }

    fun parse(content: String, decimalSeparator: String): ImportParseResult {
        if (content.isBlank()) return ImportParseResult(emptyList(), emptyList())

        // 1. Suddividiamo il contenuto in record rispettando le virgolette.
        val records = toCsvRecords(content)

        // 2. Rileviamo il separatore di colonna: primo tra , ; \t che divide in >= 3 colonne.
        val firstLine = records.firstOrNull() ?: return ImportParseResult(emptyList(), emptyList())
        val delimiter = listOf(',', ';', '\t').firstOrNull { splitLine(firstLine, it).size >= 3 }
            ?: return ImportParseResult(emptyList(), listOf(ImportError(0, "Separatore non riconosciuto")))

        val header = splitLine(firstLine, delimiter)
        val roles = detectRoles(header)
        var dateIdx = roles.entries.firstOrNull { it.value == Role.DATE }?.key ?: -1
        var descIdx = roles.entries.firstOrNull { it.value == Role.DESCRIPTION }?.key ?: -1
        var amountIdx = roles.entries.firstOrNull { it.value == Role.AMOUNT }?.key ?: -1
        var typeIdx = roles.entries.firstOrNull { it.value == Role.TYPE }?.key ?: -1
        var catIdx = roles.entries.firstOrNull { it.value == Role.CATEGORY }?.key ?: -1

        // File senza header riconosciuto ma con >= 3 colonne -> mapping posizionale.
        // In quel caso la prima riga è un dato, non un header.
        var headerLines = 1
        if ((dateIdx < 0 || descIdx < 0 || amountIdx < 0) && roles.isEmpty() && header.size >= 3) {
            dateIdx = 0
            descIdx = 1
            amountIdx = 2
            typeIdx = -1
            catIdx = -1
            headerLines = 0
        }

        if (dateIdx < 0 || descIdx < 0 || amountIdx < 0) {
            return ImportParseResult(emptyList(), listOf(ImportError(0, "Colonne obbligatorie mancanti")))
        }

        // 3. Parsing riga per riga.
        val transactions = mutableListOf<ParsedImportTransaction>()
        val errors = mutableListOf<ImportError>()
        for ((i, record) in records.withIndex()) {
            if (i < headerLines) continue // header
            if (record.isBlank()) continue
            val cols = splitLine(record, delimiter)
            if (cols.size <= maxOf(dateIdx, descIdx, amountIdx, typeIdx, catIdx)) {
                errors.add(ImportError(i + 1, "Numero di colonne insufficiente"))
                continue
            }

            val date = parseDate(cols[dateIdx])
            if (date == null) {
                errors.add(ImportError(i + 1, "Data non valida"))
                continue
            }
            val amount = parseAmount(cols[amountIdx], decimalSeparator)
            if (amount == null) {
                errors.add(ImportError(i + 1, "Importo non valido"))
                continue
            }

            val type = if (typeIdx >= 0) {
                mapType(cols[typeIdx]) ?: inferType(amount)
            } else {
                inferType(amount)
            }

            transactions.add(
                ParsedImportTransaction(
                    title = cols[descIdx].trim(),
                    amount = kotlin.math.abs(amount),
                    type = type,
                    date = date,
                    categoryName = if (catIdx >= 0 && cols[catIdx].isNotBlank()) cols[catIdx].trim() else null,
                    sourceLine = i + 1
                )
            )
        }
        return ImportParseResult(transactions, errors)
    }

    /** Raggruppa le righe fisiche in record CSV, unendo quelle chiuse da una virgoletta. */
    private fun toCsvRecords(content: String): List<String> {
        val records = mutableListOf<String>()
        var pending = ""
        for (line in content.split("\n")) {
            val candidate = if (pending.isEmpty()) line else "$pending\n$line"
            if (candidate.count { it == '"' } % 2 == 1) {
                pending = candidate
            } else {
                records.add(candidate)
                pending = ""
            }
        }
        if (pending.isNotEmpty()) records.add(pending)
        return records
    }

    /** Divide una riga CSV rispettando i campi quotati con doppie virgolette. */
    private fun splitLine(line: String, delimiter: Char): List<String> {
        val cols = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i += 2
                }
                c == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                c == delimiter && !inQuotes -> {
                    cols.add(current.toString())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(c)
                    i++
                }
            }
        }
        cols.add(current.toString())
        return cols.map { it.trim() }
    }

    /** Associa ogni colonna header a un ruolo, normalizzando minuscole/accnti. */
    private fun detectRoles(header: List<String>): Map<Int, Role> {
        val roles = mutableMapOf<Int, Role>()
        header.forEachIndexed { index, raw ->
            val h = raw.lowercase(Locale.ROOT)
                .replace('à', 'a').replace('è', 'e').replace('ì', 'i')
                .replace('ò', 'o').replace('ù', 'u')
            val role = when {
                // data: date/data/fecha, oppure "trans date", "data operazione", "data valuta"
                h == "date" || h == "data" || h == "fecha" ||
                    (h.contains("date") && (h.contains("trans") || h.contains("post") || h.contains("value") || h.contains("booking"))) ||
                    (h.contains("data") && (h.contains("valuta") || h.contains("operaz"))) -> Role.DATE
                // descrizione: desc*, memo, notes, payee/merchant/name, titolo/concepto
                h.contains("desc") || h.contains("memo") || h.contains("note") ||
                    h == "name" || h.contains("payee") || h.contains("merchant") ||
                    h.contains("titolo") || h.contains("concepto") ||
                    h == "dettagli" || h == "descr" -> Role.DESCRIPTION
                // importo: importo/amount/value/trnamt, oppure debit/credit separati
                h == "amount" || h == "importo" || h == "value" || h == "trnamt" ||
                    h == "debit" || h == "credit" || h == "addebito" || h == "accredito" -> Role.AMOUNT
                // tipo
                h == "type" || h == "tipo" || h.contains("trntype") -> Role.TYPE
                // categoria
                h.contains("categoria") || h.contains("category") || h == "cat" -> Role.CATEGORY
                else -> null
            }
            if (role != null) roles[index] = role
        }
        return roles
    }

    /** Tenta i formati: ISO yyyy-MM-dd, Europeo dd/MM/yyyy, Americano MM/dd/yyyy (solo se ambiguo), compatto yyyyMMdd. */
    private fun parseDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        fun tryFormat(pattern: String): Long? {
            return try {
                val fmt = SimpleDateFormat(pattern, Locale.ROOT)
                fmt.isLenient = false
                fmt.parse(s)?.time
            } catch (e: Exception) {
                null
            }
        }

        tryFormat("yyyy-MM-dd")?.let { return it }

        // dd/MM/yyyy con controllo rigoroso che il giorno esista davvero.
        val european = parseSlashDate(s, european = true)
        if (european != null) return european

        // MM/dd/yyyy solo quando il "giorno" europeo supererebbe 12 (file americani).
        val american = parseSlashDate(s, european = false)
        if (american != null) return american

        tryFormat("yyyyMMdd")?.let { return it }
        return null
    }

    /**
     * Parsing manuale di date slash-separated. Con [european] = true interpreta
     * giorno/mese/anno e restituisce null se il giorno non esiste in quel mese.
     * Con [european] = false interpreta mese/giorno/anno.
     */
    private fun parseSlashDate(s: String, european: Boolean): Long? {
        val parts = s.split("/", "-").map { it.trim() }
        if (parts.size != 3 || parts[2].length != 4) return null
        val a = parts[0].toIntOrNull() ?: return null
        val b = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        val day = if (european) a else b
        val month = if (european) b else a
        if (month !in 1..12) return null
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }
        if (cal.get(Calendar.DAY_OF_MONTH) != day || cal.get(Calendar.MONTH) != month - 1) return null
        return cal.timeInMillis
    }

    private fun parseAmount(raw: String, decimalSeparator: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        // Rimuove simboli valuta, spazi e lettere
        val cleaned = s.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        if (cleaned.isEmpty()) return null
        val normalized = when (decimalSeparator) {
            "," -> cleaned.replace(".", "").replace(",", ".").toDoubleOrNull()
            else -> cleaned.replace(",", "").toDoubleOrNull()
        }
        return normalized
    }

    private fun mapType(raw: String): String? {
        return when (val t = raw.trim().lowercase(Locale.ROOT)) {
            "debit", "expense", "uscita", "pagamento", "pos", "fee" -> "EXPENSE"
            "credit", "income", "entrata", "dep", "int", "div" -> "INCOME"
            else -> null
        }
    }

    private fun inferType(amount: Double): String =
        if (amount < 0) "EXPENSE" else "INCOME"
}