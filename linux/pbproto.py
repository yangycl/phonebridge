# Copyright (C) 2026 PhoneBridge authors
# SPDX-License-Identifier: GPL-3.0-or-later

from __future__ import annotations

import json
import os
import socket
import struct
from typing import Any


class ProtocolError(Exception):
    pass


def send_msg(sock: socket.socket, obj: dict[str, Any], data: bytes = b"") -> None:
    if data:
        obj = dict(obj)
        obj["data_len"] = len(data)
    raw = json.dumps(obj, separators=(",", ":")).encode("utf-8")
    sock.sendall(struct.pack(">I", len(raw)) + raw + data)


def recv_exact(sock: socket.socket, n: int) -> bytes:
    buf = bytearray()
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ProtocolError("connection closed")
        buf.extend(chunk)
    return bytes(buf)


def recv_msg(sock: socket.socket) -> tuple[dict[str, Any], bytes]:
    (jlen,) = struct.unpack(">I", recv_exact(sock, 4))
    if jlen > 8 * 1024 * 1024:
        raise ProtocolError("json too large")
    obj = json.loads(recv_exact(sock, jlen).decode("utf-8"))
    data_len = int(obj.get("data_len") or 0)
    data = recv_exact(sock, data_len) if data_len else b""
    return obj, data


def safe_join(root: str, rel: str) -> str:
    rel = rel.replace("\\", "/").strip()
    if rel in ("", "."):
        rel = ""
    if rel.startswith("/") or ".." in rel.split("/"):
        raise ProtocolError("bad path")
    path = os.path.abspath(os.path.join(root, rel))
    root_abs = os.path.abspath(root)
    if path != root_abs and not path.startswith(root_abs + os.sep):
        raise ProtocolError("path escapes root")
    return path
