#!/usr/bin/env python3
# Copyright (C) 2026 PhoneBridge authors
# SPDX-License-Identifier: GPL-3.0-or-later
"""Linux client: connect to a PhoneBridge device and FUSE-mount it."""

from __future__ import annotations

import argparse
import errno
import os
import socket
import stat
import sys
import threading
import time

from pbproto import ProtocolError, recv_msg, send_msg

try:
    from fuse import FUSE, FuseOSError, Operations
except ImportError:
    FUSE = None
    FuseOSError = OSError  # type: ignore
    Operations = object  # type: ignore


class PhoneClient:
    def __init__(self, host: str, port: int, pin: str) -> None:
        self.host = host
        self.port = port
        self.pin = pin
        self.sock: socket.socket | None = None
        self.lock = threading.Lock()
        self.next_id = 1
        self.hello: dict = {}

    def connect(self) -> None:
        s = socket.create_connection((self.host, self.port), timeout=10)
        s.settimeout(30)
        self.sock = s
        send_msg(s, {"op": "hello", "ver": 1, "pin": self.pin})
        obj, _ = recv_msg(s)
        if obj.get("op") != "hello_ok":
            raise ProtocolError(f"hello failed: {obj}")
        self.hello = obj

    def _rpc(self, req: dict, data: bytes = b"") -> tuple[dict, bytes]:
        if self.sock is None:
            raise ProtocolError("not connected")
        with self.lock:
            req = dict(req)
            req["id"] = self.next_id
            self.next_id += 1
            send_msg(self.sock, req, data)
            obj, blob = recv_msg(self.sock)
        if obj.get("op") == "err":
            raise ProtocolError(obj.get("code", "err"))
        return obj, blob

    def listdir(self, path: str) -> list[dict]:
        obj, _ = self._rpc({"op": "list", "path": path})
        return list(obj.get("entries") or [])

    def stat(self, path: str) -> dict:
        obj, _ = self._rpc({"op": "stat", "path": path})
        return obj

    def read(self, path: str, offset: int, length: int) -> bytes:
        _, blob = self._rpc(
            {"op": "read", "path": path, "offset": offset, "length": length}
        )
        return blob


class PhoneFS(Operations):
    def __init__(self, client: PhoneClient) -> None:
        self.c = client
        self.uid = os.getuid()
        self.gid = os.getgid()

    def _attr(self, info: dict) -> dict:
        mode = stat.S_IFDIR | 0o555 if info.get("dir") else stat.S_IFREG | 0o444
        now = time.time()
        mtime = float(info.get("mtime") or now)
        return {
            "st_mode": mode,
            "st_nlink": 2 if info.get("dir") else 1,
            "st_size": int(info.get("size") or 0),
            "st_uid": self.uid,
            "st_gid": self.gid,
            "st_atime": mtime,
            "st_mtime": mtime,
            "st_ctime": mtime,
        }

    def getattr(self, path, fh=None):
        rel = "." if path == "/" else path.lstrip("/")
        try:
            if rel == ".":
                return self._attr({"dir": True, "size": 0, "mtime": time.time()})
            return self._attr(self.c.stat(rel))
        except ProtocolError as e:
            raise FuseOSError(errno.ENOENT) from e

    def readdir(self, path, fh):
        rel = "." if path == "/" else path.lstrip("/")
        yield "."
        yield ".."
        try:
            for ent in self.c.listdir(rel):
                name = ent.get("name")
                if name:
                    yield name
        except ProtocolError:
            return

    def read(self, path, size, offset, fh):
        rel = path.lstrip("/")
        try:
            return self.c.read(rel, offset, size)
        except ProtocolError as e:
            raise FuseOSError(errno.EIO) from e


def cmd_fuse(args: argparse.Namespace) -> int:
    if FUSE is None:
        print("需要 fusepy：pip install fusepy 或安裝發行版的 python3-fuse", file=sys.stderr)
        return 1
    client = PhoneClient(args.host, args.port, args.pin)
    client.connect()
    print(
        f"connected mode={client.hello.get('mode')} caps={client.hello.get('caps')}",
        file=sys.stderr,
    )
    FUSE(PhoneFS(client), args.mountpoint, foreground=True, nothreads=False, ro=True)
    return 0


def cmd_nbd(_args: argparse.Namespace) -> int:
    print(
        "NBD/NVMe 後端尚未實作。v0 請用 fuse 子命令讓檔案總管掛載。",
        file=sys.stderr,
    )
    return 2


def main() -> int:
    p = argparse.ArgumentParser(description="PhoneBridge Linux client")
    sub = p.add_subparsers(dest="cmd", required=True)

    f = sub.add_parser("fuse", help="FUSE-mount the phone")
    f.add_argument("--host", required=True)
    f.add_argument("--port", type=int, default=17420)
    f.add_argument("--pin", default="1234")
    f.add_argument("mountpoint")
    f.set_defaults(func=cmd_fuse)

    n = sub.add_parser("nbd", help="export as NBD (not implemented)")
    n.set_defaults(func=cmd_nbd)

    args = p.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
