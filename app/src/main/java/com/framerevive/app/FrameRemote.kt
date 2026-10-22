package com.framerevive.app

import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.io.FilterInputStream
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLServerSocket

/** LAN-only management. A random per-install secret is required on every request. */
class FrameRemote(private val token: String, private val page: () -> String,
                  private val apply: (Map<String, String>) -> String, private val server: SSLServerSocket,
                  private val upload: ((java.io.InputStream, Int) -> String)? = null,
                  private val headerTimeoutMs: Long = 5000, private val bodyTimeoutMs: Long = 120_000) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor()
    private val requests = ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, SynchronousQueue())
    private val deadlines = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val callbacks = Any()
    val port: Int get() = server.localPort
    @Volatile private var closed = false
    init {
        worker.execute {
            while (!closed) {
                try {
                    val socket = server.accept()
                    sockets.add(socket)
                    if (closed) { sockets.remove(socket); socket.close(); continue }
                    val headerDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(headerTimeoutMs)
                    val timeout = try { deadlines.schedule({ socket.close() }, headerTimeoutMs, TimeUnit.MILLISECONDS) }
                    catch (_: java.util.concurrent.RejectedExecutionException) { sockets.remove(socket); socket.close(); continue }
                    try { requests.execute {
                        try { socket.use { serve(it, headerDeadline) { timeout.cancel(false) } } }
                        catch (_: Exception) { /* Never log request credentials. */ }
                        finally { timeout.cancel(false); sockets.remove(socket) }
                    } } catch (_: java.util.concurrent.RejectedExecutionException) {
                        timeout.cancel(false); sockets.remove(socket); socket.close()
                    }
                }
                catch (_: java.net.SocketTimeoutException) { }
                catch (_: Exception) { /* No request, token, or Google credentials are logged. */ }
            }
        }
    }
    private fun serve(socket: Socket, headerDeadline: Long, finishHeaders: () -> Unit) {
        var deadline = headerDeadline
        fun checkTime() {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw java.net.SocketTimeoutException("Request deadline")
            socket.soTimeout = minOf(4000L, TimeUnit.NANOSECONDS.toMillis(remaining) + 1).toInt()
        }
        checkTime()
        if (socket is SSLSocket) socket.startHandshake()
        val input = object : FilterInputStream(socket.getInputStream()) {
            override fun read(): Int { checkTime(); return super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { checkTime(); return `in`.read(b, off, len) }
        }
        var headerBytes = 0
        fun line(): String {
            val out = StringBuilder()
            while (out.length < 8192) {
                val c = input.read()
                check(++headerBytes <= 32_768) { "Headers too large" }
                if (c < 0 || c == 10) return out.toString().trimEnd('\r')
                out.append(c.toChar())
            }
            error("Header too large")
        }
        val request = line().split(' ')
        if (request.size < 2) return
        val headers = HashMap<String, String>()
        var headersComplete = false
        for (i in 0..40) {
            val h = line(); if (h.isEmpty()) { headersComplete = true; break }
            headers[h.substringBefore(':').lowercase()] = h.substringAfter(':').trim()
        }
        check(headersComplete) { "Too many headers" }
        val target = request[1]
        val key = target.substringAfter("?", "").split('&').firstOrNull { it.startsWith("key=") }?.substringAfter('=')
        if (key != token) { reply(socket, 403, "Management key required."); return }
        if (headers["sec-fetch-site"] == "cross-site") { reply(socket, 403, "Open your saved management link directly."); return }
        val origin = headers["origin"]
        if (origin != null && origin != "https://${headers["host"]}") { reply(socket, 403, "Invalid request origin."); return }
        if (request[0] == "GET") { reply(socket, 200, synchronized(callbacks) { page() }); return }
        if (request[0] != "POST") { reply(socket, 405, "Method not supported."); return }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        // A hard close also bounds TLS writes, callbacks and upload reads, independent of trickling bytes.
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(bodyTimeoutMs)
        val bodyTimer = deadlines.schedule({ socket.close() }, bodyTimeoutMs, TimeUnit.MILLISECONDS)
        finishHeaders()
        try {
        if (target.substringAfter('?', "").split('&').contains("action=uploadApk")) {
            if (upload == null) { reply(socket, 404, "APK upload is unavailable."); return }
            if (headers["content-type"] != "application/vnd.android.package-archive" || length !in 1..FrameUpdater.MAX_BYTES) {
                reply(socket, 400, "Choose an APK no larger than 40 MiB."); return
            }
            try { reply(socket, 200, "<p>${escape(synchronized(callbacks) { upload.invoke(input, length) })}</p>") }
            catch (_: Exception) { reply(socket, 400, "<p>APK upload or validation failed. See update status on the controls page.</p>") }
            return
        }
        if (length !in 1..800_000) { reply(socket, 400, "Form is too large. Choose a small cookies.txt export."); return }
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) { val n = input.read(bytes, read, length - read); if (n < 0) error("Incomplete form"); read += n }
        val fields = String(bytes, Charsets.UTF_8).split('&').associate { entry ->
            URLDecoder.decode(entry.substringBefore('='), "UTF-8") to URLDecoder.decode(entry.substringAfter('=', ""), "UTF-8")
        }
        val result = synchronized(callbacks) { checkTime(); apply(fields) }
        reply(socket, 200, "<!doctype html><meta name='viewport' content='width=device-width'><p>${escape(result)}</p><p><a href='?key=$token'>Back to frame settings</a></p>")
        } finally { bodyTimer.cancel(false) }
    }
    private fun reply(socket: Socket, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code Response\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; connect-src 'self'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
        // Send a clean response EOF even when an early rejection leaves form bytes unread.
        socket.shutdownOutput()
    }
    override fun close() {
        closed = true; server.close(); sockets.forEach { runCatching { it.close() } }
        worker.shutdownNow(); requests.shutdownNow(); deadlines.shutdownNow()
    }
    companion object {
        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    }
}
