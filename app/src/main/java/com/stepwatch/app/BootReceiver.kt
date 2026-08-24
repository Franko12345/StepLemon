package com.stepwatch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After reboot the system won't auto-start a service, but we don't need
 * one — the TYPE_STEP_COUNTER sensor keeps counting in the background
 * hardware (it's not stopped by boot). We only need to nudge any
 * long-running state if we ever add a service. For now this is a stub
 * to satisfy the BOOT_COMPLETED permission and stay boot-safe.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op: sensor counter persists across boot automatically.
        // Reserved for future background service startup.
    }
}