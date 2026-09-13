# PhoneBridge

以區網把普通 Android 的共用儲存匯出到 Linux，再用系統檔案管理器瀏覽。

授權：**GNU General Public License v3.0 或更新版本**（見 `LICENSE`）。

## 現況（v0）

- 手機：普通 Android App，匯出共用儲存（`/storage/emulated/0`）
- 協定：TCP + 長度前置 JSON，可讀；寫入欄位已留，由 `mode` / `caps` 控制
- Linux：FUSE 掛載（檔案總管可開）
- NBD / 虛擬 NVMe：目錄與介面已留，尚未實作區塊裝置

沒有 root 時看不到其他 App 的私有資料。可寫權限以後用 capability 往上加，不必換協定。

## 快速試（不需手機）

```bash
# 終端機 A：假手機，匯出目前目錄
python3 tools/fake_phone.py --root . --pin 1234 --port 17420

# 終端機 B
sudo python3 linux/phonebridge.py fuse --host 127.0.0.1 --port 17420 --pin 1234 /mnt/phone
xdg-open /mnt/phone
```

依賴：`python3`、`fuse`（套件名通常是 `fuse3` + `python3-fuse` 或自行裝 `fusepy`）。

連真機：手機與電腦同一 LAN，App 顯示埠與 PIN，把 `--host` 換成手機 IP。

## 目錄

- `proto/` 協定說明
- `linux/` Linux 用戶端
- `android/` Android App
- `tools/fake_phone.py` 本機假裝置，方便先測 Linux

## 權限檔次（規劃）

1. 使用者選資料夾（SAF）
2. 所有檔案存取 → 共用儲存可寫
3. Shizuku / ADB
4. root（整機樹，可寫預設仍建議關）

## 法律

Copyright (C) 2026 PhoneBridge authors

本程式為自由軟體，依 GPLv3 或更新版本授權。不提供任何擔保。
