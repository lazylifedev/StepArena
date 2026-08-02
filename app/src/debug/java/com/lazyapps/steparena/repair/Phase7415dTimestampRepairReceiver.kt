package com.lazyapps.steparena.repair

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Phase7415dTimestampRepairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = Phase7415dTimestampRepair.execute(
                    context,
                    intent.getStringExtra("repairId").orEmpty(),
                    intent.getStringExtra("manifestSha").orEmpty(),
                )
                Log.i("Phase7415dRepair", "status=${result.status} after=${result.afterFingerprint}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.lazyapps.steparena.debug.PHASE_7_4_15D_TIMESTAMP_REPAIR"
    }
}
