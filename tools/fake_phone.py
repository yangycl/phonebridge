#!/usr/bin/env python3
# Copyright (C) 2026 PhoneBridge authors
# SPDX-License-Identifier: GPL-3.0-or-later
"""Local stand-in for the Android exporter."""

from __future__ import annotations

import argparse
import os
import socket
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "linux"))
from pbproto import ProtocolError, recv_msg, safe_join, send_msg  # noqa: E402


def handle(conn: socket.socket, root: str, pin: str, mode: str) -> None:
    obj, _ = recv_msg(conn)
    if obj.get("op") != "hello" or str(obj.get("pin")) != pin:
        send_msg(conn, {"op": "err", "code": "auth"})
        return
    caps = ["list", "stat", "read"]
    if mode == "rw":
        caps += ["write", "create", "delete", "rename"]
    send_msg(
        conn,
        {
            "op": "hello_ok",
            "ver": 1,
            "mode": mode,
            "caps": caps,
            "root": os.path.abspath(root),
            "device": "fake-phone",
        },
    )
    while True:
        try:
            req, blob = recv_msg(conn)
        except ProtocolError:
            return
        rid = req.get("id")
        op = req.get("op")
        try:
            if op == "list":
                path = safe_join(root, req.get("path", "."))
                entries = []
                for name in sorted(os.listdir(path)):
                    p = os.path.join(path, name)
                    st = os.stat(p)
                    entries.append(
                        {
                            "name": name,
                            "dir": os.path.isdir(p),
                            "size": st.st_size,
                            "mtime": int(st.st_mtime),
                        }
                    )
                send_msg(conn, {"op": "ok", "id": rid, "entries": entries})
            elif op == "stat":
                path = safe_join(root, req.get("path", "."))
                st = os.stat(path)
                send_msg(
                    conn,
                    {
                        "op": "ok",
                        "id": rid,
                        "dir": os.path.isdir(path),
                        "size": st.st_size,
                        "mtime": int(st.st_mtime),
                    },
                )
            elif op == "read":
                path = safe_join(root, req.get("path", "."))
                offset = int(req.get("offset") or 0)
                length = int(req.get("length") or 0)
                if length < 0 or length > 8 * 1024 * 1024:
                    raise ProtocolError("bad length")
                with open(path, "rb") as fh:
                    fh.seek(offset)
                    data = fh.read(length)
                send_msg(conn, {"op": "ok", "id": rid}, data)
            elif op in ("write", "create", "delete", "rename"):
                if mode != "rw":
                    send_msg(conn, {"op": "err", "id": rid, "code": "ro"})
                    continue
                path = safe_join(root, req.get("path", "."))
                if op == "write":
                    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
                    flags = os.O_RDWR | os.O_CREAT
                    fd = os.open(path, flags, 0o644)
                    try:
                        os.lseek(fd, int(req.get("offset") or 0), os.SEEK_SET)
                        os.write(fd, blob)
                    finally:
                        os.close(fd)
                    send_msg(conn, {"op": "ok", "id": rid})
                elif op == "create":
                    open(path, "ab").close()
                    send_msg(conn, {"op": "ok", "id": rid})
                elif op == "delete":
                    if os.path.isdir(path):
                        os.rmdir(path)
                    else:
                        os.remove(path)
                    send_msg(conn, {"op": "ok", "id": rid})
                elif op == "rename":
                    dest = safe_join(root, req.get("to", ""))
                    os.rename(path, dest)
                    send_msg(conn, {"op": "ok", "id": rid})
            else:
                send_msg(conn, {"op": "err", "id": rid, "code": "unknown"})
        except FileNotFoundError:
            send_msg(conn, {"op": "err", "id": rid, "code": "enoent"})
        except ProtocolError as e:
            send_msg(conn, {"op": "err", "id": rid, "code": str(e)})
        except OSError as e:
            send_msg(conn, {"op": "err", "id": rid, "code": e.__class__.__name__})


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--root", default=".")
    p.add_argument("--pin", default="1234")
    p.add_argument("--port", type=int, default=17420)
    p.add_argument("--mode", choices=("ro", "rw"), default="ro")
    args = p.parse_args()
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", args.port))
    srv.listen(1)
    print(f"fake phone on 0.0.0.0:{args.port} pin={args.pin} root={args.root}")
    while True:
        conn, addr = srv.accept()
        print("peer", addr)
        try:
            handle(conn, args.root, args.pin, args.mode)
        finally:
            conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
