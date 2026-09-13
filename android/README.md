# PhoneBridge Android exporter

普通 App：在區網聽 TCP `17420`，用 PIN 配對，匯出外部共用儲存。

v0 以原始碼為主。用 Android Studio 開這個目錄建 APK。

需要權限：

- `INTERNET`
- `FOREGROUND_SERVICE`
- Android 11+：`MANAGE_EXTERNAL_STORAGE`（設定裡手動開「所有檔案存取」）

寫入：`BridgeServer` 目前 `mode=ro`。要開寫入時把 hello 的 `mode`/`caps` 改掉，並在對應 op 裡呼叫 `FileOutputStream`。沒有這項權限時不要回 `rw`。
