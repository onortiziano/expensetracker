package it.ciano.expensetracker.data.ingest

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseIngestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val params = mutableMapOf<String, String?>()
        params.putAll(IntentParamsExtractor.extractExtras(intent))
        intent?.data?.let { params.putAll(IntentParamsExtractor.extractQuery(it)) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                ExpenseIngest.process(applicationContext, params)
            } finally {
                runOnUiThread { finish() }
            }
        }
    }
}
