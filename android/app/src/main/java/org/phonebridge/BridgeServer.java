/* Copyright (C) 2026 PhoneBridge authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.phonebridge;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class BridgeServer {
    public static final int PORT = 17420;
    private static final int MAX_JSON = 8 * 1024 * 1024;
    private static final int MAX_READ = 8 * 1024 * 1024;

    private final File root;
    private final String pin;

    public BridgeServer(File root, String pin) {
        this.root = root;
        this.pin = pin;
    }

    public void serveForever(AtomicBoolean running) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            server.setSoTimeout(1000);
            while (running.get()) {
                try {
                    Socket sock = server.accept();
                    new Thread(() -> {
                        try {
                            handle(sock);
                        } finally {
                            try {
                                sock.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }, "phonebridge-conn").start();
                } catch (SocketTimeoutException ignored) {
                }
            }
        }
    }

    private void handle(Socket sock) {
        try {
            DataInputStream input = new DataInputStream(sock.getInputStream());
            DataOutputStream output = new DataOutputStream(sock.getOutputStream());
            JSONObject hello = readObj(input);
            if (!"hello".equals(hello.optString("op")) || !pin.equals(hello.optString("pin"))) {
                writeObj(output, new JSONObject().put("op", "err").put("code", "auth"), null);
                return;
            }
            JSONArray caps = new JSONArray();
            caps.put("list");
            caps.put("stat");
            caps.put("read");
            caps.put("write");
            caps.put("create");
            caps.put("delete");
            caps.put("rename");
            writeObj(output, new JSONObject()
                    .put("op", "hello_ok")
                    .put("ver", 1)
                    .put("mode", "rw")
                    .put("caps", caps)
                    .put("root", root.getAbsolutePath())
                    .put("device", Build.MODEL), null);

            while (true) {
                JSONObject req;
                try {
                    req = readObj(input);
                } catch (IOException e) {
                    return;
                }
                int extra = req.optInt("data_len", 0);
                Object id = req.opt("id");
                String op = req.optString("op");
                byte[] blob = null;
                if ("write".equals(op) && extra > 0) {
                    if (extra > MAX_READ) {
                        writeObj(output, err(id, "toobig"), null);
                        skipFully(input, extra);
                        continue;
                    }
                    blob = new byte[extra];
                    input.readFully(blob);
                } else if (extra > 0) {
                    skipFully(input, extra);
                }
                try {
                    switch (op) {
                        case "list":
                            writeObj(output, list(req, id), null);
                            break;
                        case "stat":
                            writeObj(output, stat(req, id), null);
                            break;
                        case "read":
                            readFile(req, id, output);
                            break;
                        case "ping":
                            writeObj(output, new JSONObject().put("op", "pong").put("id", id), null);
                            break;
                        case "write":
                            writeFile(req, id, blob, output);
                            break;
                        case "create":
                            writeObj(output, createFile(req, id), null);
                            break;
                        case "delete":
                            writeObj(output, deleteFile(req, id), null);
                            break;
                        case "rename":
                            writeObj(output, renameFile(req, id), null);
                            break;
                        default:
                            writeObj(output, err(id, "unknown"), null);
                    }
                } catch (FileNotFoundException e) {
                    writeObj(output, err(id, "enoent"), null);
                } catch (SecurityException e) {
                    writeObj(output, err(id, "badpath"), null);
                } catch (Exception e) {
                    String msg = e.getMessage();
                    writeObj(output, err(id, msg != null ? msg : "err"), null);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private File resolve(String rel) throws IOException {
        if (rel == null) {
            rel = ".";
        }
        rel = rel.replace('\\', '/').trim();
        if (".".equals(rel) || rel.isEmpty()) {
            rel = "";
        }
        if (rel.startsWith("/") || Arrays.asList(rel.split("/")).contains("..")) {
            throw new SecurityException("bad path");
        }
        File f = new File(root, rel).getCanonicalFile();
        File base = root.getCanonicalFile();
        String fp = f.getPath();
        String bp = base.getPath();
        if (!fp.equals(bp) && !fp.startsWith(bp + File.separator)) {
            throw new SecurityException("escape");
        }
        return f;
    }

    private JSONObject list(JSONObject req, Object id) throws Exception {
        File dir = resolve(req.optString("path", "."));
        JSONArray entries = new JSONArray();
        File[] children = dir.listFiles();
        if (children != null) {
            Arrays.sort(children, (a, b) -> a.getName().compareTo(b.getName()));
            for (File child : children) {
                entries.put(new JSONObject()
                        .put("name", child.getName())
                        .put("dir", child.isDirectory())
                        .put("size", child.length())
                        .put("mtime", child.lastModified() / 1000));
            }
        }
        return new JSONObject().put("op", "ok").put("id", id).put("entries", entries);
    }

    private JSONObject stat(JSONObject req, Object id) throws Exception {
        File f = resolve(req.optString("path", "."));
        if (!f.exists()) {
            throw new FileNotFoundException();
        }
        return new JSONObject()
                .put("op", "ok")
                .put("id", id)
                .put("dir", f.isDirectory())
                .put("size", f.length())
                .put("mtime", f.lastModified() / 1000);
    }

    private void readFile(JSONObject req, Object id, DataOutputStream output) throws Exception {
        File f = resolve(req.optString("path", "."));
        long offset = req.optLong("offset", 0);
        int length = req.optInt("length", 0);
        if (length < 0) {
            length = 0;
        }
        if (length > MAX_READ) {
            length = MAX_READ;
        }
        byte[] data;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(offset);
            byte[] buf = new byte[length];
            int n = raf.read(buf);
            data = n <= 0 ? new byte[0] : Arrays.copyOf(buf, n);
        }
        writeObj(output, new JSONObject().put("op", "ok").put("id", id), data);
    }

    private void writeFile(JSONObject req, Object id, byte[] blob, DataOutputStream output)
            throws Exception {
        if (blob == null) {
            blob = new byte[0];
        }
        File f = resolve(req.optString("path", "."));
        if (f.isDirectory()) {
            writeObj(output, err(id, "isdir"), null);
            return;
        }
        long offset = req.optLong("offset", 0);
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.seek(offset);
            raf.write(blob);
        }
        writeObj(output, new JSONObject().put("op", "ok").put("id", id).put("n", blob.length), null);
    }

    private JSONObject createFile(JSONObject req, Object id) throws Exception {
        File f = resolve(req.optString("path", "."));
        if (f.isDirectory()) {
            return err(id, "isdir");
        }
        f.createNewFile();
        return new JSONObject().put("op", "ok").put("id", id);
    }

    private JSONObject deleteFile(JSONObject req, Object id) throws Exception {
        File f = resolve(req.optString("path", "."));
        if (!f.exists()) {
            throw new FileNotFoundException();
        }
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null && children.length > 0) {
                return err(id, "notempty");
            }
        }
        if (!f.delete()) {
            return err(id, "err");
        }
        return new JSONObject().put("op", "ok").put("id", id);
    }

    private JSONObject renameFile(JSONObject req, Object id) throws Exception {
        File from = resolve(req.optString("path", "."));
        File to = resolve(req.optString("to", ""));
        if (!from.renameTo(to)) {
            return err(id, "rename");
        }
        return new JSONObject().put("op", "ok").put("id", id);
    }

    private static JSONObject err(Object id, String code) throws Exception {
        return new JSONObject().put("op", "err").put("id", id).put("code", code);
    }

    private static JSONObject readObj(DataInputStream input) throws IOException {
        int n = input.readInt();
        if (n < 0 || n > MAX_JSON) {
            throw new IOException("bad json");
        }
        byte[] buf = new byte[n];
        input.readFully(buf);
        try {
            return new JSONObject(new String(buf, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException("bad json", e);
        }
    }

    private static void writeObj(DataOutputStream output, JSONObject obj, byte[] data) throws Exception {
        if (data != null && data.length > 0) {
            obj.put("data_len", data.length);
        }
        byte[] raw = obj.toString().getBytes(StandardCharsets.UTF_8);
        output.writeInt(raw.length);
        output.write(raw);
        if (data != null && data.length > 0) {
            output.write(data);
        }
        output.flush();
    }

    private static void skipFully(DataInputStream input, int n) throws IOException {
        while (n > 0) {
            long skipped = input.skip(n);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new IOException("eof");
                }
                n--;
            } else {
                n -= (int) skipped;
            }
        }
    }
}
