/* Copyright (C) 2026 PhoneBridge authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.phonebridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean

class BridgeService : Service() {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pin = intent?.getStringExtra("pin") ?: "1234"
        startForeground(1, notice())
        if (running.compareAndSet(false, true)) {
            val root = Environment.getExternalStorageDirectory()
            thread = Thread {
                try {
                    BridgeServer(root, pin).serveForever(running)
                } catch (_: Exception) {
                } finally {
                    running.set(false)
                }
            }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        thread?.interrupt()
        super.onDestroy()
    }

    private fun notice(): Notification {
        val id = "phonebridge"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(id, "PhoneBridge", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, id)
        else Notification.Builder(this)
        return b.setContentTitle("PhoneBridge")
            .setContentText("正在匯出共用儲存")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()
    }
}
