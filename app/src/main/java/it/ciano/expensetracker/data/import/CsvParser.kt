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

    /** Titolo segnaposto usato quando il file non ha una colonna descrizione. */
    const val PLACEHOLDER_TITLE = "Importato da file"

    /** Ragione canonica quando la colonna Importo non viene mappata. */
    const val REASON_AMOUNT_NOT_MAPPED = "Importo non mappato"

    /** Mapping manuale colonne file → campi app. -1 = colonna non usata.
     *  [typeDefault]: se valorizzato ("EXPENSE"/"INCOME") forza il tipo su tutte
     *  le righe, ignorando colonna tipo e segno. null = comportamento normale. */
    data class ImportMapping(
        val dateIdx: Int = -1,
        val descIdx: Int = -1,
        val amountIdx: Int = -1,
        val typeIdx: Int = -1,
        val catIdx: Int = -1,
        val typeDefault: String? = null
    )

    /** Esito della rilevazione: header, riga di esempio e suggerimenti di mapping. */
    data class CsvFileInfo(
        val headers: List<String>,
        val sampleRow: List<String>,
        val suggested: ImportMapping
    )

    /**
     * Rileva struttura del CSV senza bloccarsi: header, prima riga dati e
     * mapping suggerito (euristiche). Il chiamante mostra la UI di mapping.
     * null se il contenuto non è CSV riconoscibile (vuoto o senza separatore).
     */
    fun detectFile(content: String): CsvFileInfo? {
        if (content.isBlank()) return null
        val records = toCsvRecords(content)
        val firstLine = records.firstOrNull() ?: return null
        val delimiter = listOf(',', ';', '\t').firstOrNull { splitLine(firstLine, it).size >= 3 }
            ?: return null
        val headers = splitLine(firstLine, delimiter)
        val roles = detectRoles(headers)

        var suggested = ImportMapping(
            dateIdx = roles.entries.firstOrNull { it.value == Role.DATE }?.key ?: -1,
            descIdx = roles.entries.firstOrNull { it.value == Role.DESCRIPTION }?.key ?: -1,
            amountIdx = roles.entries.firstOrNull { it.value == Role.AMOUNT }?.key ?: -1,
            typeIdx = roles.entries.firstOrNull { it.value == Role.TYPE }?.key ?: -1,
            catIdx = roles.entries.firstOrNull { it.value == Role.CATEGORY }?.key ?: -1
        )

        // Nessun ruolo rilevato ma >= 3 colonne -> molto probabile file senza header:
        // suggeriamo il mapping posizionale (data;desc;importo).
        if (roles.isEmpty() && headers.size >= 3) {
            suggested = ImportMapping(dateIdx = 0, descIdx = 1, amountIdx = 2)
        }

        val sampleRow = records.getOrNull(1)?.let { splitLine(it, delimiter) } ?: emptyList()
        return CsvFileInfo(headers, sampleRow, suggested)
    }

    /** Parsing automatico (legacy): euristiche + fallback posizionale.
     *  Rifiuta il file quando data/descrizione/importo non sono individuabili. */
    fun parse(content: String, decimalSeparator: String): ImportParseResult {
        if (content.isBlank()) return ImportParseResult(emptyList(), emptyList())

        val records = toCsvRecords(content)
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

        return parseRecords(
            records, delimiter, decimalSeparator,
            ImportMapping(dateIdx, descIdx, amountIdx, typeIdx, catIdx),
            headerLines
        )
    }

    /** Parsing con mapping esplicito (UI di mapping). headerLines = 1 per default. */
    fun parse(content: String, decimalSeparator: String, mapping: ImportMapping, headerLines: Int = 1): ImportParseResult {
        if (content.isBlank()) return ImportParseResult(emptyList(), emptyList())
        val records = toCsvRecords(content)
        val firstLine = records.firstOrNull() ?: return ImportParseResult(emptyList(), emptyList())
        // Con mapping esplicito basta che il separatore divida in >= 2 colonne
        // (file minimi tipo "Importo;Data" sono leciti, a differenza dell'auto-detect).
        val delimiter = listOf(',', ';', '\t').firstOrNull { splitLine(firstLine, it).size >= 2 }
            ?: return ImportParseResult(emptyList(), listOf(ImportError(0, "Separatore non riconosciuto")))

        if (mapping.amountIdx < 0) {
            return ImportParseResult(emptyList(), listOf(ImportError(0, REASON_AMOUNT_NOT_MAPPED)))
        }
        return parseRecords(records, delimiter, decimalSeparator, mapping, headerLines)
    }

    private fun parseRecords(
        records: List<String>,
        delimiter: Char,
        decimalSeparator: String,
        mapping: ImportMapping,
        headerLines: Int
    ): ImportParseResult {
        val transactions = mutableListOf<ParsedImportTransaction>()
        val errors = mutableListOf<ImportError>()
        val maxIdx = maxOf(mapping.dateIdx, mapping.descIdx, mapping.amountIdx, mapping.typeIdx, mapping.catIdx)

        for ((i, record) in records.withIndex()) {
            if (i < headerLines) continue // header
            if (record.isBlank()) continue
            val cols = splitLine(record, delimiter)
            if (cols.size <= maxIdx) {
                errors.add(ImportError(i + 1, "Numero di colonne insufficiente"))
                continue
            }

            val date = if (mapping.dateIdx >= 0) {
                parseDate(cols[mapping.dateIdx])
            } else {
                todayStartOfDay()
            }
            if (date == null) {
                errors.add(ImportError(i + 1, "Data non valida"))
                continue
            }
            val amount = if (mapping.amountIdx >= 0) parseAmount(cols[mapping.amountIdx], decimalSeparator) else null
            if (amount == null) {
                errors.add(ImportError(i + 1, "Importo non valido"))
                continue
            }

            val title = if (mapping.descIdx >= 0) cols[mapping.descIdx].trim() else PLACEHOLDER_TITLE

            val type = when {
                mapping.typeDefault != null -> mapping.typeDefault
                mapping.typeIdx >= 0 -> mapType(cols[mapping.typeIdx]) ?: inferType(amount)
                else -> inferType(amount)
            }

            transactions.add(
                ParsedImportTransaction(
                    title = title.ifBlank { PLACEHOLDER_TITLE },
                    amount = kotlin.math.abs(amount),
                    type = type,
                    date = date,
                    categoryName = if (mapping.catIdx >= 0 && cols[mapping.catIdx].isNotBlank()) cols[mapping.catIdx].trim() else null,
                    sourceLine = i + 1
                )
            )
        }
        return ImportParseResult(transactions, errors)
    }

    private fun todayStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Raggruppa le righe fisiche in record CSV, unendo quelle chiuse da una virgoletta.
     *  Normalizza i delimitatori di riga (\r\n, \r, \n): alcuni export usano solo CR. */
    private fun toCsvRecords(content: String): List<String> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val records = mutableListOf<String>()
        var pending = ""
        for (line in normalized.split("\n")) {
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
                // data: date/data/fecha, "data e ora", "trans date", "data operazione", "data valuta", "date time", ...
                h == "date" || h == "data" || h == "fecha" ||
                    (h.contains("date") && (h.contains("trans") || h.contains("post") || h.contains("value") || h.contains("booking") || h.contains(" time") || h.contains("time "))) ||
                    (h.contains("data") && (h.contains("valuta") || h.contains("operaz") || h.contains(" ora"))) ||
                    h.contains("data e ora") || h.contains("data/ora") -> Role.DATE
                // descrizione: desc*, memo, notes, payee/merchant/name, titolo/concepto
                h.contains("desc") || h.contains("memo") || h.contains("note") ||
                    h == "name" || h.contains("payee") || h.contains("merchant") ||
                    h.contains("titolo") || h.contains("concepto") ||
                    h == "dettagli" || h == "descr" -> Role.DESCRIPTION
                // importo: importo/amount/value/trnamt, oppure debit/credit separati.
                // contains: "Importo ( € )" deve essere riconosciuto.
                h.contains("amount") || h.contains("importo") || h == "value" || h == "trnamt" ||
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

    /** Tenta i formati: ISO yyyy-MM-dd, Europeo dd/MM/yyyy, Americano MM/dd/yyyy (solo se ambiguo), compatto yyyyMMdd, con nome mese. */
    private fun parseDate(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        fun tryFormat(pattern: String, locale: Locale = Locale.ROOT): Long? {
            return try {
                val fmt = SimpleDateFormat(pattern, locale)
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

        // Date con nome del mese locale, es. "20 lug 2026", "5 lug 2026, 13:17", "20 Jul 2026".
        // L'ora viene azzerata: per l'import conta solo il giorno.
        val mese = listOf("dd MMM yyyy, HH:mm", "dd MMM yyyy").firstNotNullOfOrNull { pattern ->
            listOf(Locale.ITALIAN, Locale.ENGLISH).firstNotNullOfOrNull { locale ->
                tryFormat(pattern, locale)
            }
        }
        if (mese != null) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = mese
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

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

    private fun parseAmount(raw: String, _decimalSeparator: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        // Rimuove simboli valuta, spazi e lettere
        val cleaned = s.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
        if (cleaned.isEmpty()) return null
        return normalizeAmountToDouble(cleaned)
    }

    /** Normalizza un importo (solo cifre, [','] ['.'] ['-']) a Double.
     *  Euristica robusta sui file bancari reali, che spesso mescolano i separatori:
     *  - separatore decimale = l'ultimo tra , e . seguito da AL MASSIMO 2 cifre
     *    (non importa il separatore preferito: "203.80" e "103.5" restano 203,80 e 103,5
     *    anche se la preferenza è la virgola);
     *  - ogni altro separatore con >= 3 cifre finali è un separatore dei migliaia
     *    (es. "1.234" = 1234). */
    private fun normalizeAmountToDouble(cleaned: String): Double? {
        val negative = cleaned.startsWith('-')
        val body = cleaned.removePrefix("-")
        if (body.isEmpty()) return null

        val lastSep = maxOf(body.lastIndexOf(','), body.lastIndexOf('.'))
        val frac = if (lastSep >= 0) body.substring(lastSep + 1) else ""

        // Separatore decimale = l'ultimo tra , e . seguito da 1-2 cifre finali
        // (es. "203.80", "-1,3", "1,234.56"). Copre i file che mescolano i separatori.
        val decChar: Char? = if (lastSep >= 0 && frac.length in 1..2) body[lastSep] else null

        if (decChar != null) {
            val intPart = body.substring(0, lastSep).replace(".", "").replace(",", "")
            if (intPart.isEmpty()) return null
            val sign = if (negative) "-" else ""
            return "$sign$intPart.$frac".toDoubleOrNull()
        }
        // Nessun decimale: ogni separatore con 3+ cifre finali è dei migliaia (es. "1.234").
        val n = body.replace(".", "").replace(",", "")
        if (n.isEmpty()) return null
        val sign = if (negative) "-" else ""
        return (sign + n).toDoubleOrNull()
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