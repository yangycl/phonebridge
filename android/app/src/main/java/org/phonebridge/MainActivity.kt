/* Copyright (C) 2026 PhoneBridge authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.phonebridge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin = (1000 + Random.nextInt(9000)).toString()
        val text = TextView(this)
        text.textSize = 18f
        text.text = "PIN $pin\n埠 17420\n請在系統設定開啟「所有檔案存取」後按開始。"
        val start = Button(this).apply { this.text = "開始匯出" }
        val stop = Button(this).apply { this.text = "停止" }
        start.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                return@setOnClickListener
            }
            val i = Intent(this, BridgeService::class.java)
            i.putExtra("pin", pin)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
        stop.setOnClickListener { stopService(Intent(this, BridgeService::class.java)) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(text)
            addView(start)
            addView(stop)
        }
        setContentView(layout)
    }
}
