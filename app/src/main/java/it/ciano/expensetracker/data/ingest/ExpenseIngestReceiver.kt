package it.ciano.expensetracker.data.ingest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseIngestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ExpenseIngest.process(context.applicationContext, IntentParamsExtractor.extractExtras(intent))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
