/* Copyright (C) 2026 PhoneBridge authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.phonebridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import java.util.concurrent.atomic.AtomicBoolean;

public class BridgeService extends Service {
    public static final String EXTRA_PIN = "pin";
    private static final String CHANNEL_ID = "phonebridge";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String pin = intent != null ? intent.getStringExtra(EXTRA_PIN) : null;
        if (pin == null || pin.isEmpty()) {
            pin = "1234";
        }
        startForeground(1, notice());
        if (running.compareAndSet(false, true)) {
            final String usePin = pin;
            worker = new Thread(() -> {
                try {
                    new BridgeServer(Environment.getExternalStorageDirectory(), usePin)
                            .serveForever(running);
                } catch (Exception ignored) {
                } finally {
                    running.set(false);
                }
            }, "phonebridge-server");
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running.set(false);
        if (worker != null) {
            worker.interrupt();
        }
        super.onDestroy();
    }

    private Notification notice() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "PhoneBridge", NotificationManager.IMPORTANCE_LOW));
            }
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("PhoneBridge")
                .setContentText("正在匯出共用儲存")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .build();
    }
}
