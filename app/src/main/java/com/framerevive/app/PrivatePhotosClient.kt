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
class PrivatePhotosClient(private val context: Context, private val anonymous: Boolean = false) {
    data class SharedAlbum(val title: String, val photos: List<FramePhoto>, val itemCount: Int?)
    private var lastAddress = ""
    private var sourcePath = "/"
    data class Album(val id: String, val title: String, val count: Int)
    private val cookieFile = File(context.filesDir, "google-session.enc")
    private var cookies = mutableListOf<GoogleSessionCookie>()
    private var persistCookies = true
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
            val parsed = GoogleSessionCookies.parse(incoming.readText())
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
                GoogleSessionCookie(c.getString("domain"), c.getString("path"), c.getString("name"), c.getString("value"), c.getLong("expires"))
            }.toMutableList()
        }
    }

    @Synchronized private fun saveCookies() {
        if (!persistCookies) return
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
        (url.host == "photos.google.com" || (anonymous && url.host == "photos.app.goo.gl") || url.host == "photos.fife.usercontent.google.com" || url.host.endsWith(".googleusercontent.com"))

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
                val cookie = if (anonymous) "" else header(url)
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
                lastAddress = url.toString()
                if (!anonymous) synchronized(this) {
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
        if (cookies.isEmpty()) importSessionIfPresent()
        check(cookies.isNotEmpty()) { "Waiting for local Google session setup." }
        val html = String(request("https://photos.google.com/"), Charsets.UTF_8)
        val text = Regex("window\\.WIZ_global_data\\s*=\\s*(\\{.*?\\})\\s*;", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: error("Google sign-in needs renewal or the website changed.")
        global = JSONObject(text)
        for (field in listOf("FdrFJe", "cfb2h", "SNlM0e", "Im6cmf")) check(!global!!.optString(field).isBlank()) { "Incomplete Google session." }
    }

    private fun rpc(id: String, data: JSONArray): JSONArray {
        require(id == "snAcKc" || id == "Z5xsfc") // Read albums only; no Google Photos write operations.
        val g = global ?: error("Not connected")
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val query = "rpcids=$id&source-path=${enc(sourcePath)}&f.sid=${enc(g.getString("FdrFJe"))}&bl=${enc(g.getString("cfb2h"))}&rt=c"
        val payload = JSONArray().put(JSONArray().put(JSONArray().put(id).put(data.toString()).put(JSONObject.NULL).put("generic")))
        val body = "f.req=${enc(payload.toString())}" + if (g.optString("SNlM0e").isNotBlank()) "&at=${enc(g.getString("SNlM0e"))}" else ""
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

    fun albums(): List<Album> {
        connect()
        var token: String? = null
        val seen = HashSet<String>()
        val result = LinkedHashMap<String, Album>()
        do {
            val page = rpc("Z5xsfc", JSONArray().put(token ?: JSONObject.NULL).put(JSONObject.NULL).put(JSONObject.NULL)
                .put(JSONObject.NULL).put(1).put(JSONObject.NULL).put(JSONObject.NULL).put(100).put(JSONArray().put(2)).put(5))
            val rows = page.optJSONArray(0) ?: JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONArray(i) ?: continue
                val meta = row.optJSONObject(row.length() - 1)?.optJSONArray("72930366")
                val id = row.optString(0)
                if (id.isNotEmpty()) result[id] = Album(id, meta?.optString(1)?.takeIf { it.isNotEmpty() } ?: "Untitled album", meta?.optInt(3) ?: 0)
            }
            token = if (page.isNull(1)) null else page.optString(1).takeIf { it.isNotEmpty() }
            if (token != null) check(seen.add(token)) { "Album pagination repeated. Try again later." }
        } while (token != null)
        return result.values.toList()
    }

    /** Validate in a separate in-memory client; an invalid upload never replaces the saved session. */
    fun validateSession(text: String): Pair<PrivatePhotosClient, List<Album>> {
        val candidate = PrivatePhotosClient(context)
        candidate.persistCookies = false
        candidate.cookies = GoogleSessionCookies.parse(text).toMutableList()
        val albums = candidate.albums()
        candidate.persistCookies = true
        candidate.saveCookies()
        return candidate to albums
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

    private fun sharedPage(link: String): Pair<JSONArray, String> {
        check(anonymous)
        val supplied = URL(link)
        require(supplied.protocol == "https" && supplied.port == -1 && supplied.userInfo == null &&
            (supplied.host == "photos.app.goo.gl" || (supplied.host == "photos.google.com" && supplied.path.startsWith("/share/")))) {
            "Use a Google Photos shared album link."
        }
        val html = String(request(link), Charsets.UTF_8)
        val resolved = URL(lastAddress)
        require(resolved.host == "photos.google.com" && resolved.path.startsWith("/share/")) { "The link did not open a shared album." }
        sourcePath = resolved.path
        val metadata = Regex("window\\.WIZ_global_data\\s*=\\s*(\\{.*?\\})\\s*;", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: error("Shared album page format changed.")
        global = JSONObject(metadata)
        val blocks = Regex("AF_initDataCallback\\(\\{key: '[^']+'.*?data:(.*?), sideChannel:", RegexOption.DOT_MATCHES_ALL)
        var page: JSONArray? = null
        for (match in blocks.findAll(html)) {
            val candidate = runCatching { JSONArray(match.groupValues[1]) }.getOrNull() ?: continue
            if (candidate.optJSONArray(1) != null && candidate.optJSONArray(3)?.optString(0)?.isNotBlank() == true) { page = candidate; break }
        }
        val current = page ?: error("The shared album is unavailable or its format changed.")
        val album = current.getJSONArray(3)
        val authKey = resolved.query?.split('&')?.firstOrNull { it.startsWith("key=") }?.substringAfter('=')
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: album.optString(19)
        require(authKey.isNotBlank()) { "Shared album access key is missing." }
        return current to authKey
    }

    /** Only the public initial page, not the complete paginated photo index. */
    fun sharedCount(link: String): Int? = sharedPage(link).first.getJSONArray(3).optInt(21, -1).takeIf { it >= 0 }

    /** Read the shared page and every subsequent album page without account cookies. */
    fun syncShared(link: String, progress: (Int) -> Unit): SharedAlbum {
        val (first, authKey) = sharedPage(link)
        var current = first
        val album = current.getJSONArray(3)
        val albumKey = album.getString(0)
        val title = album.optString(1).ifBlank { "Shared album" }
        val photos = LinkedHashMap<String, FramePhoto>()
        val seen = HashSet<String>()
        while (true) {
            check(!Thread.currentThread().isInterrupted) { "Sync interrupted" }
            val rows = current.optJSONArray(1) ?: JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONArray(i) ?: continue
                val id = row.optString(0); val url = row.optJSONArray(1)?.optString(0).orEmpty()
                if (id.isNotBlank() && url.isNotBlank() && !row.isNull(2))
                    photos[id] = FramePhoto(id, url, row.getLong(2), if (row.isNull(4)) 0 else row.optLong(4))
            }
            progress(photos.size)
            val token = if (current.isNull(2)) null else current.optString(2).takeIf { it.isNotEmpty() }
            if (token == null) break
            check(seen.add(token)) { "Shared album pagination repeated; previous index retained." }
            current = rpc("snAcKc", JSONArray().put(albumKey).put(token).put(JSONObject.NULL).put(authKey))
        }
        check(photos.isNotEmpty()) { "Shared album returned no photos; previous index retained." }
        return SharedAlbum(title, photos.values.toList(), album.optInt(21, -1).takeIf { it >= 0 })
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
