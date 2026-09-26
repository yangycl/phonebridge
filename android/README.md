# PhoneBridge Android exporter

Java App：區網聽 TCP `17420`，PIN 配對，匯出外部共用儲存。

用 Android Studio 開 `android/` 建 APK。原始碼在 `app/src/main/java/org/phonebridge/`：

- `MainActivity.java` PIN / IP / 開始停止
- `BridgeService.java` 前景服務
- `BridgeServer.java` 協定（list / stat / read，寫入回 `ro`）

權限：`INTERNET`、`FOREGROUND_SERVICE`、Android 11+ 的「所有檔案存取」。
