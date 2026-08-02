package com.lazyapps.steparena.repair

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Phase7415RepairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = Phase7415Repair.execute(
                    context,
                    intent.getStringExtra("repairId").orEmpty(),
                    intent.getStringExtra("manifestSha").orEmpty(),
                )
                Log.i("Phase7415Repair", "status=${result.status} fingerprint=${result.fingerprint}")
            } catch (t: Throwable) {
                Log.e("Phase7415Repair", "Repair rejected", t)
            } finally { pending.finish() }
        }
    }
}
