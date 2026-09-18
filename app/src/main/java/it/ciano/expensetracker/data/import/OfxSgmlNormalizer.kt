package it.ciano.expensetracker.data.import

/**
 * Converte un documento OFX legacy (SGML) in XML ben formato, pronto per
 * essere letto da XmlPullParser. Funzione pura e testabile su JVM.
 *
 * I file OFX legacy mescolano tre stili di tag:
 *  - tag "nudi" strutturali (es. `SONRS`) che vengono chiusi da `</SONRS>`;
 *  - tag di valore sulla stessa riga (`<TRNTYPE>CREDIT`) senza chiusura;
 *  - tag con attributi (`STATUS SEVERITY="INFO"`).
 * Lo scanner qui sotto riconosce tutti e tre e li appiattisce in XML valido.
 */
object OfxSgmlNormalizer {

    fun isXml(input: String): Boolean {
        val first = input.lines().firstOrNull { it.isNotBlank() } ?: return false
        return first.trimStart().startsWith("<?xml")
    }

    fun normalizeToXml(input: String): String {
        if (isXml(input)) return input
        return transformSgml(input)
    }

    private val headerRegex = Regex("""(?m)^[A-Z][A-Z0-9]*:[^\r\n]*(\r?\n|$)+""")

    private fun transformSgml(input: String): String {
        // 1. Rimuove l'header OFX (coppie CHIAVE:VALORE fino alla riga vuota).
        var content = headerRegex.replace(input, "")

        // 2. Incapsula in <OFX> se manca la radice.
        val trimmed = content.trim()
        if (!trimmed.startsWith("<OFX>")) {
            content = "<OFX>\n$trimmed\n</OFX>"
        }

        // 3. Scanner a stati SGML -> XML.
        val out = StringBuilder()
        val openTags = ArrayDeque<String>()
        var i = 0
        while (i < content.length) {
            val c = content[i]
            i = if (c == '<') {
                parseMarkedTag(content, i, out, openTags)
            } else {
                val end = content.indexOf('<', i).let { if (it == -1) content.length else it }
                emitTextBlock(content.substring(i, end), out, openTags)
                end
            }
        }
        // 4. Chiude eventuali tag strutturali rimasti aperti.
        while (openTags.isNotEmpty()) out.append("</").append(openTags.removeLast()).append(">")
        return out.toString()
    }

    /** Gestisce un tag delimitato da '<'. Ritorna il nuovo indice di scansione. */
    private fun parseMarkedTag(content: String, start: Int, out: StringBuilder, openTags: ArrayDeque<String>): Int {
        var j = start + 1
        if (j < content.length && content[j] == '/') {
            // Tag di chiusura.
            val gt = content.indexOf('>', j)
            val end = if (gt == -1) content.length else gt
            val name = content.substring(j + 1, end).trim()
            closeTag(name, out, openTags)
            return if (gt == -1) end else gt + 1
        }

        // Tag di apertura: nome.
        val nameStart = j
        while (j < content.length && content[j] in 'A'..'Z') j++
        val name = content.substring(nameStart, j)

        // Attributi opzionali: ATTR="value".
        val attrs = StringBuilder()
        while (true) {
            val beforeWhitespace = j
            while (j < content.length && (content[j] == ' ' || content[j] == '\t' || content[j] == '\r' || content[j] == '\n')) j++
            val attrStart = j
            var k = j
            while (k < content.length && (content[k] in 'A'..'Z' || content[k] in '0'..'9' || content[k] == '_')) k++
            if (k < content.length && content[k] == '=' && k + 1 < content.length && content[k + 1] == '"') {
                val quoteEnd = content.indexOf('"', k + 2)
                if (quoteEnd != -1) {
                    val attrName = content.substring(attrStart, k)
                    val attrValue = content.substring(k + 2, quoteEnd)
                    attrs.append('<').append(attrName).append('>').append(attrValue).append("</").append(attrName).append('>')
                    j = quoteEnd + 1
                    continue
                }
            }
            j = beforeWhitespace
            break
        }

        if (j < content.length && content[j] == '>') {
            j++
            // Valore sulla stessa riga (stile `<TRNTYPE>CREDIT`).
            val valueEnd = j
            var v = j
            while (v < content.length && content[v] != '<' && content[v] != '\n' && content[v] != '\r') v++
            val value = content.substring(valueEnd, v).trim()
            if (value.isEmpty()) {
                // Tag strutturale: verrà chiuso da un </TAG> successivo.
                out.append('<').append(name).append('>').append(attrs)
                openTags.addLast(name)
            } else if (matchesClosingTag(content, v, name)) {
                // Il valore è già seguito dal suo </TAG>: emettiamo solo l'apertura
                // e lasciamo che il <TAG> di chiusura venga processato dal loop.
                out.append('<').append(name).append('>').append(attrs).append(value)
            } else {
                // Tag di valore senza chiusura esplicita: lo chiudiamo subito.
                out.append('<').append(name).append('>').append(attrs).append(value).append("</").append(name).append('>')
            }
            return v
        }

        // '>' omesso (stile SGML puro): trattiamo come tag strutturale.
        out.append('<').append(name).append('>').append(attrs)
        openTags.addLast(name)
        return j
    }

    /** True se in corrispondenza di [pos] inizia `</name` (chiusura esplicita). */
    private fun matchesClosingTag(content: String, pos: Int, name: String): Boolean {
        return pos + 2 + name.length <= content.length &&
            content.startsWith("</", pos) &&
            content.regionMatches(pos + 2, name, 0, name.length)
    }

    /** Blocco di testo: può contenere tag "nudi" strutturali oppure valori da ignorare. */
    private fun emitTextBlock(block: String, out: StringBuilder, openTags: ArrayDeque<String>) {
        for (line in block.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Tag nudo con attributi: `STATUS SEVERITY="INFO"`.
            val bareAttr = Regex("""^([A-Z][A-Z0-9_]*)\s+([A-Z][A-Z0-9_]*=\"[^\"]*\")(?:\s+[A-Z][A-Z0-9_]*=\"[^\"]*\")*$""").find(trimmed)
            if (bareAttr != null) {
                val name = bareAttr.groupValues[1]
                val attrs = Regex("""[A-Z][A-Z0-9_]*=\"[^\"]*\"""").findAll(bareAttr.groupValues[0])
                    .map { m ->
                        val parts = m.value.split("=", limit = 2)
                        "<${parts[0]}>${parts[1].trim('"')}</${parts[0]}>"
                    }
                    .joinToString("")
                out.append('<').append(name).append('>').append(attrs)
                openTags.addLast(name)
                continue
            }

            // Tag nudo strutturale: `SONRS`, `CODE0`.
            if (trimmed.matches(Regex("""[A-Z][A-Z0-9_]+"""))) {
                out.append('<').append(trimmed).append('>')
                openTags.addLast(trimmed)
                continue
            }

            // Valore testuale tra un tag e l'altro: non utile allo stmt, lo ignoriamo.
            out.append(' ')
        }
    }

    /**
     * Chiude un tag. Se il nome non è nello stack il tag era già stato chiuso
     * inline (es. `<TRNTYPE>CREDIT`): ci limitiamo a emettere la chiusura.
     * Altrimenti chiudiamo prima i figli strutturali rimasti aperti.
     */
    private fun closeTag(name: String, out: StringBuilder, openTags: ArrayDeque<String>) {
        if (!openTags.contains(name)) {
            out.append("</").append(name).append('>')
            return
        }
        while (openTags.isNotEmpty() && openTags.last() != name) {
            out.append("</").append(openTags.removeLast()).append('>')
        }
        openTags.removeLast()
        out.append("</").append(name).append('>')
    }
}