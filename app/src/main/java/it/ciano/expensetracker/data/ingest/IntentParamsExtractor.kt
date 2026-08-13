package it.ciano.expensetracker.data.ingest

import android.content.Intent
import android.net.Uri

object IntentParamsExtractor {

    fun extractExtras(intent: Intent?): Map<String, String?> {
        val extras = intent?.extras ?: return emptyMap()
        val map = mutableMapOf<String, String?>()
        for (key in extras.keySet()) {
            map[key] = extras.get(key)?.toString()
        }
        return map
    }

    fun extractQuery(uri: Uri): Map<String, String?> {
        val names = uri.queryParameterNames ?: return emptyMap()
        val map = mutableMapOf<String, String?>()
        for (name in names) {
            map[name] = uri.getQueryParameter(name)
        }
        return map
    }
}
