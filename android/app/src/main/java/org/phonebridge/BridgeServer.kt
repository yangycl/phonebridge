/* Copyright (C) 2026 PhoneBridge authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.phonebridge

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class BridgeServer(private val root: File, private val pin: String) {
    fun serveForever(running: AtomicBoolean) {
        ServerSocket(17420).use { server ->
            server.soTimeout = 1000
            while (running.get()) {
                try {
                    val sock = server.accept()
                    Thread { sock.use { handle(it) } }.start()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handle(sock: java.net.Socket) {
        val input = DataInputStream(sock.getInputStream())
        val output = DataOutputStream(sock.getOutputStream())
        val hello = readObj(input)
        if (hello.optString("op") != "hello" || hello.optString("pin") != pin) {
            writeObj(output, JSONObject().put("op", "err").put("code", "auth"))
            return
        }
        writeObj(
            output,
            JSONObject()
                .put("op", "hello_ok")
                .put("ver", 1)
                .put("mode", "ro")
                .put("caps", JSONArray(listOf("list", "stat", "read")))
                .put("root", root.absolutePath)
                .put("device", android.os.Build.MODEL)
        )
        while (true) {
            val req = try {
                readObj(input)
            } catch (_: Exception) {
                return
            }
            val extra = req.optInt("data_len", 0)
            if (extra > 0) input.skipBytes(extra)
            val id = req.opt("id")
            val op = req.optString("op")
            try {
                when (op) {
                    "list" -> writeObj(output, list(req, id))
                    "stat" -> writeObj(output, stat(req, id))
                    "read" -> readFile(req, id, output)
                    "write", "create", "delete", "rename" ->
                        writeObj(output, JSONObject().put("op", "err").put("id", id).put("code", "ro"))
                    else ->
                        writeObj(output, JSONObject().put("op", "err").put("id", id).put("code", "unknown"))
                }
            } catch (_: java.io.FileNotFoundException) {
                writeObj(output, JSONObject().put("op", "err").put("id", id).put("code", "enoent"))
            } catch (e: Exception) {
                writeObj(output, JSONObject().put("op", "err").put("id", id).put("code", e.message ?: "err"))
            }
        }
    }

    private fun resolve(rel: String): File {
        val cleaned = rel.replace('\\', '/').trim().let { if (it == "." || it.isEmpty()) "" else it }
        if (cleaned.startsWith("/") || cleaned.split("/").contains("..")) {
            throw SecurityException("bad path")
        }
        val f = File(root, cleaned).canonicalFile
        val base = root.canonicalFile
        if (f != base && !f.path.startsWith(base.path + File.separator)) {
            throw SecurityException("escape")
        }
        return f
    }

    private fun list(req: JSONObject, id: Any?): JSONObject {
        val dir = resolve(req.optString("path", "."))
        val entries = JSONArray()
        dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
            entries.put(
                JSONObject()
                    .put("name", child.name)
                    .put("dir", child.isDirectory)
                    .put("size", child.length())
                    .put("mtime", child.lastModified() / 1000)
            )
        }
        return JSONObject().put("op", "ok").put("id", id).put("entries", entries)
    }

    private fun stat(req: JSONObject, id: Any?): JSONObject {
        val f = resolve(req.optString("path", "."))
        if (!f.exists()) throw java.io.FileNotFoundException()
        return JSONObject()
            .put("op", "ok")
            .put("id", id)
            .put("dir", f.isDirectory)
            .put("size", f.length())
            .put("mtime", f.lastModified() / 1000)
    }

    private fun readFile(req: JSONObject, id: Any?, output: DataOutputStream) {
        val f = resolve(req.optString("path", "."))
        val offset = req.optLong("offset", 0)
        val length = req.optInt("length", 0).coerceIn(0, 8 * 1024 * 1024)
        java.io.RandomAccessFile(f, "r").use { raf ->
            raf.seek(offset)
            val buf = ByteArray(length)
            val n = raf.read(buf)
            val data = if (n <= 0) ByteArray(0) else buf.copyOf(n)
            writeObj(output, JSONObject().put("op", "ok").put("id", id), data)
        }
    }

    private fun readObj(input: DataInputStream): JSONObject {
        val n = input.readInt()
        if (n < 0 || n > 8 * 1024 * 1024) throw IllegalArgumentException("bad json")
        val buf = ByteArray(n)
        input.readFully(buf)
        return JSONObject(String(buf, Charsets.UTF_8))
    }

    private fun writeObj(output: DataOutputStream, obj: JSONObject, data: ByteArray = ByteArray(0)) {
        if (data.isNotEmpty()) obj.put("data_len", data.size)
        val raw = obj.toString().toByteArray(Charsets.UTF_8)
        output.writeInt(raw.size)
        output.write(raw)
        if (data.isNotEmpty()) output.write(data)
        output.flush()
    }
}
