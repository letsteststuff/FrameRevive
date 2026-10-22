package com.framerevive.app

import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/** LAN-only management. A random per-install secret is required on every request. */
class FrameRemote(private val token: String, private val page: () -> String,
                  private val apply: (Map<String, String>) -> String, port: Int = 8765) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private val server = ServerSocket(port).apply { soTimeout = 1000 }
    val port: Int get() = server.localPort
    @Volatile private var closed = false
    init {
        worker.execute {
            while (!closed) {
                try { server.accept().use { serve(it) } }
                catch (_: java.net.SocketTimeoutException) { }
                catch (_: Exception) { /* No request, token, or Google credentials are logged. */ }
            }
        }
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 4000
        val input = socket.getInputStream()
        fun line(): String {
            val out = StringBuilder()
            while (out.length < 8192) {
                val c = input.read()
                if (c < 0 || c == 10) return out.toString().trimEnd('\r')
                out.append(c.toChar())
            }
            error("Header too large")
        }
        val request = line().split(' ')
        if (request.size < 2) return
        val headers = HashMap<String, String>()
        for (i in 0..40) {
            val h = line(); if (h.isEmpty()) break
            headers[h.substringBefore(':').lowercase()] = h.substringAfter(':').trim()
        }
        val target = request[1]
        val key = target.substringAfter("?", "").split('&').firstOrNull { it.startsWith("key=") }?.substringAfter('=')
        if (key != token) { reply(socket, 403, "Management key required."); return }
        if (headers["sec-fetch-site"] == "cross-site") { reply(socket, 403, "Open your saved management link directly."); return }
        val origin = headers["origin"]
        if (origin != null && origin != "http://${headers["host"]}") { reply(socket, 403, "Invalid request origin."); return }
        if (request[0] == "GET") { reply(socket, 200, page()); return }
        if (request[0] != "POST") { reply(socket, 405, "Method not supported."); return }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length !in 1..800_000) { reply(socket, 400, "Form is too large. Choose a small cookies.txt export."); return }
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) { val n = input.read(bytes, read, length - read); if (n < 0) error("Incomplete form"); read += n }
        val fields = String(bytes, Charsets.UTF_8).split('&').associate { entry ->
            URLDecoder.decode(entry.substringBefore('='), "UTF-8") to URLDecoder.decode(entry.substringAfter('=', ""), "UTF-8")
        }
        val result = apply(fields)
        reply(socket, 200, "<!doctype html><meta name='viewport' content='width=device-width'><p>${escape(result)}</p><p><a href='?key=$token'>Back to frame settings</a></p>")
    }
    private fun reply(socket: Socket, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code Response\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; connect-src 'self'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
        // Send a clean response EOF even when an early rejection leaves form bytes unread.
        socket.shutdownOutput()
    }
    override fun close() { closed = true; server.close(); worker.shutdownNow() }
    companion object {
        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    }
}
