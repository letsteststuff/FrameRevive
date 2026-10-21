package com.framerevive.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Read-only wire format adapted from xob0t/google_photos_web_client (MIT).
// See tools/GOOGLE_PHOTOS_CLIENT_LICENSE.txt. This web API is unofficial.
class PrivatePhotosClient(private val context: Context) {
    data class Cookie(val domain: String, val path: String, val name: String, val value: String, val expires: Long)
    private val cookieFile = File(context.filesDir, "google-session.enc")
    private var cookies = mutableListOf<Cookie>()
    private var global: JSONObject? = null

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("framerevive-session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("framerevive-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun importSessionIfPresent() {
        val incoming = File(context.filesDir, "session-import.txt")
        if (incoming.exists()) {
            val parsed = incoming.readLines().mapNotNull { line ->
                val clean = line.removePrefix("#HttpOnly_")
                if (clean.startsWith("#") || clean.isBlank()) return@mapNotNull null
                val cols = clean.split('\t')
                if (cols.size < 7) return@mapNotNull null
                val domain = cols[0].removePrefix(".")
                if (domain !in setOf("google.com", "photos.google.com", "photos.fife.usercontent.google.com")) return@mapNotNull null
                Cookie(cols[0], cols[2], cols[5], cols[6], cols[4].toLongOrNull() ?: 0)
            }
            require(parsed.isNotEmpty()) { "No Google Photos session found in import." }
            cookies = parsed.toMutableList()
            saveCookies()
            check(incoming.delete()) { "Session saved, but import file could not be removed." }
        } else if (cookieFile.exists()) {
            val bytes = cookieFile.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val json = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            cookies = (0 until json.length()).map { i ->
                val c = json.getJSONObject(i)
                Cookie(c.getString("domain"), c.getString("path"), c.getString("name"), c.getString("value"), c.getLong("expires"))
            }.toMutableList()
        }
    }

    @Synchronized private fun saveCookies() {
        val json = JSONArray()
        cookies.forEach { c -> json.put(JSONObject().put("domain", c.domain).put("path", c.path)
            .put("name", c.name).put("value", c.value).put("expires", c.expires)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        writeAtomic(cookieFile, cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)))
    }

    @Synchronized private fun header(url: URL): String = cookies.filter {
        val domain = it.domain.removePrefix(".")
        (url.host == domain || (it.domain.startsWith(".") && url.host.endsWith(".$domain"))) &&
            url.path.startsWith(it.path) && (it.expires == 0L || it.expires > System.currentTimeMillis() / 1000)
    }.joinToString("; ") { "${it.name}=${it.value}" }

    private fun allowed(url: URL): Boolean = url.protocol == "https" &&
        (url.host == "photos.google.com" || url.host == "photos.fife.usercontent.google.com" || url.host.endsWith(".googleusercontent.com"))

    // Manual redirects ensure credentials are never forwarded to a different host.
    fun request(address: String, body: String? = null, image: Boolean = false): ByteArray {
        var url = URL(address)
        for (redirect in 0..5) {
            require(allowed(url)) { "Google sign-in needs renewal, or the image service changed." }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 25000
                connection.readTimeout = 35000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                val cookie = header(url)
                if (cookie.isNotEmpty()) connection.setRequestProperty("Cookie", cookie)
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status in 300..399) {
                    url = URL(url, connection.getHeaderField("Location") ?: error("Missing redirect location"))
                    continue
                }
                check(status in 200..299) { "Google returned HTTP $status. Cached photos remain available." }
                if (image) check(connection.contentType?.startsWith("image/") == true) { "Image access needs sign-in renewal." }
                // Accept rotations only for already imported cookie names and domains.
                synchronized(this) {
                    var changed = false
                    connection.headerFields.entries.filter { it.key?.equals("Set-Cookie", true) == true }.flatMap { it.value }.forEach { raw ->
                        val pair = raw.substringBefore(';').split('=', limit = 2)
                        if (pair.size == 2) {
                            cookies = cookies.map { c ->
                                if (c.name == pair[0] && (url.host == c.domain.removePrefix(".") ||
                                    (c.domain.startsWith(".") && url.host.endsWith(c.domain)))) {
                                    changed = true; c.copy(value = pair[1])
                                } else c
                            }.toMutableList()
                        }
                    }
                    if (changed) saveCookies()
                }
                return connection.inputStream.use { input ->
                    val limit = if (image) 12_000_000 else 25_000_000
                    val buffer = ByteArray(16384)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= limit) { "Response exceeds size limit." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects; sign-in may need renewal.")
    }

    fun connect() {
        importSessionIfPresent()
        check(cookies.isNotEmpty()) { "Waiting for local Google session setup." }
        val html = String(request("https://photos.google.com/"), Charsets.UTF_8)
        val text = Regex("window\\.WIZ_global_data\\s*=\\s*(\\{.*?\\})\\s*;", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: error("Google sign-in needs renewal or the website changed.")
        global = JSONObject(text)
        for (field in listOf("FdrFJe", "cfb2h", "SNlM0e", "Im6cmf")) check(!global!!.optString(field).isBlank()) { "Incomplete Google session." }
    }

    private fun rpc(id: String, data: JSONArray): JSONArray {
        require(id == "snAcKc") // Fetch album only; no Google Photos write operations.
        val g = global ?: error("Not connected")
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val query = "rpcids=$id&source-path=%2F&f.sid=${enc(g.getString("FdrFJe"))}&bl=${enc(g.getString("cfb2h"))}&rt=c"
        val payload = JSONArray().put(JSONArray().put(JSONArray().put(id).put(data.toString()).put(JSONObject.NULL).put("generic")))
        val body = "f.req=${enc(payload.toString())}&at=${enc(g.getString("SNlM0e"))}"
        val path = g.getString("Im6cmf")
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains('?'))
        val text = String(request("https://photos.google.com$path/data/batchexecute?$query", body), Charsets.UTF_8)
        for (line in text.lineSequence().filter { it.contains("\"wrb.fr\"") }) {
            val batch = JSONArray(line)
            for (i in 0 until batch.length()) {
                val row = batch.optJSONArray(i) ?: continue
                if (row.optString(0) == "wrb.fr" && row.optString(1) == id && !row.isNull(2)) return JSONArray(row.getString(2))
            }
        }
        error("Google album response changed or sign-in expired.")
    }

    fun sync(albumId: String, progress: (Int) -> Unit): List<FramePhoto> {
        connect()
        var token: String? = null
        val seen = HashSet<String>()
        val photos = LinkedHashMap<String, FramePhoto>()
        do {
            check(!Thread.currentThread().isInterrupted) { "Sync interrupted" }
            val page = rpc("snAcKc", JSONArray().put(albumId).put(token ?: JSONObject.NULL).put(JSONObject.NULL).put(JSONObject.NULL))
            val rows = page.optJSONArray(1) ?: JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONArray(i) ?: continue
                val id = row.optString(0)
                val url = row.optJSONArray(1)?.optString(0) ?: ""
                if (id.isNotEmpty() && url.isNotEmpty() && !row.isNull(2))
                    photos[id] = FramePhoto(id, url, row.getLong(2), if (row.isNull(4)) 0 else row.optLong(4))
            }
            progress(photos.size)
            token = if (page.isNull(2)) null else page.optString(2).takeIf { it.isNotEmpty() }
            if (token != null) check(seen.add(token)) { "Repeated Google page; old photo index retained." }
        } while (token != null)
        check(photos.isNotEmpty()) { "No photos returned; old photo index retained." }
        return photos.values.toList()
    }

    companion object {
        fun writeAtomic(file: File, bytes: ByteArray) {
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try { stream.write(bytes); atomic.finishWrite(stream) }
            catch (e: Exception) { atomic.failWrite(stream); throw e }
        }
    }
}
