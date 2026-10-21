package com.framerevive.app

data class GoogleSessionCookie(val domain: String, val path: String, val name: String, val value: String, val expires: Long)

object GoogleSessionCookies {
    fun parse(text: String, nowSeconds: Long = System.currentTimeMillis() / 1000): List<GoogleSessionCookie> {
        require(text.toByteArray(Charsets.UTF_8).size <= 200_000) { "Session file is too large. Export a dedicated Google Photos session." }
        val cookies = text.lineSequence().mapNotNull { line ->
            val clean = line.removePrefix("#HttpOnly_")
            if (clean.startsWith("#") || clean.isBlank()) return@mapNotNull null
            val cols = clean.split('\t')
            if (cols.size != 7) return@mapNotNull null
            val domain = cols[0].removePrefix(".").lowercase()
            if (domain !in setOf("google.com", "photos.google.com", "photos.fife.usercontent.google.com")) return@mapNotNull null
            val expires = cols[4].toLongOrNull() ?: return@mapNotNull null
            if (expires != 0L && expires <= nowSeconds) return@mapNotNull null
            if (!cols[2].startsWith("/") || !Regex("[A-Za-z0-9_!#$%&'*+.^`|~-]+").matches(cols[5]) || cols[6].any { it == '\r' || it == '\n' || it == ';' }) return@mapNotNull null
            GoogleSessionCookie(cols[0].lowercase(), cols[2], cols[5], cols[6], expires)
        }.associateBy { Triple(it.domain, it.path, it.name) }.values.toList()
        require(cookies.isNotEmpty()) { "No unexpired Google Photos cookies found. Choose a Netscape cookies.txt export, not JSON." }
        return cookies
    }
}
