package com.travelingsalesman.agap.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.core.bluetooth.BleMeshService
import com.travelingsalesman.agap.worker.SyncWorker

/** Restores the emergency relay and persisted periodic sync after a device boot or app update. */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val appContext = context.applicationContext
        SyncWorker.schedulePeriodicSync(appContext)

        if (BleMeshService.hasRequiredPermissions(appContext)) {
            BleMeshService.start(appContext)
            BleMeshLog.i("SERVICE_RESTORED_AFTER_SYSTEM_EVENT", "action" to intent.action)
        } else {
            BleMeshLog.w("SERVICE_RESTORE_SKIPPED_MISSING_PERMISSION", "action" to intent.action)
        }
    }
}
