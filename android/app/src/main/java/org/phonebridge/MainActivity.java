/* Copyright (C) 2026 PhoneBridge authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.phonebridge;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private String pin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pin = String.valueOf(1000 + new Random().nextInt(9000));

        TextView text = new TextView(this);
        text.setTextSize(18f);
        text.setText(statusText());

        Button start = new Button(this);
        start.setText("開始匯出");
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                return;
            }
            Intent i = new Intent(this, BridgeService.class);
            i.putExtra(BridgeService.EXTRA_PIN, pin);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        });

        Button stop = new Button(this);
        stop.setText("停止");
        stop.setOnClickListener(v -> stopService(new Intent(this, BridgeService.class)));

        Button refresh = new Button(this);
        refresh.setText("重新顯示配對碼");
        refresh.setOnClickListener(v -> text.setText(statusText()));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);
        layout.addView(text);
        layout.addView(start);
        layout.addView(stop);
        layout.addView(refresh);
        setContentView(layout);
    }

    private String statusText() {
        String ip = localIpv4();
        return "配對碼 " + ipv4Code(ip)
                + "\nPIN " + pin
                + "\n埠 " + BridgeServer.PORT
                + "\nIP " + ip
                + "\n請先在系統設定開啟「所有檔案存取」。";
    }

    static String ipv4Code(String ip) {
        try {
            byte[] raw = InetAddress.getByName(ip).getAddress();
            if (raw.length != 4) {
                return "------";
            }
            return Base64.encodeToString(raw, Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (Exception e) {
            return "------";
        }
    }

    static String localIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "(找不到)";
    }
}
