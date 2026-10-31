package com.smmpaysync.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs == null || msgs.isEmpty()) return

        val app = context.applicationContext
        val from = msgs[0].originatingAddress ?: ""
        val provider = Providers.byHeader(from)
        if (provider == null) {
            Prefs(app).note("SMS from '$from' ignored (not a bKash sender)")
            return
        }
        val body = msgs.joinToString("") { it.messageBody ?: "" }

        val pending = goAsync()
        Thread {
            try {
                // Same order as the working reference app: start the foreground service
                // FIRST, so the process stays alive while the SMS is saved and sent.
                MonitorService.ensure(app)
                SmsIngest.handle(app, provider, body, sendInline = true)
            } catch (e: Exception) {
                Prefs(app).note("error while saving SMS: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
