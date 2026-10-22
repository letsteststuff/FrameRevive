package com.framerevive.app

import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.security.auth.x500.X500Principal

/** Small DER encoder for a self-signed RSA server certificate; no external crypto provider. */
object FrameCertificate {
    private fun der(tag: Int, bytes: ByteArray): ByteArray {
        val size = bytes.size
        val length = if (size < 128) byteArrayOf(size.toByte()) else {
            val encoded = BigInteger.valueOf(size.toLong()).toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            byteArrayOf((128 + encoded.size).toByte()) + encoded
        }
        return byteArrayOf(tag.toByte()) + length + bytes
    }
    private fun seq(vararg values: ByteArray) = der(0x30, values.fold(byteArrayOf()) { a, b -> a + b })
    private fun integer(value: BigInteger) = der(2, value.toByteArray())
    private fun oid(vararg bytes: Int) = der(6, bytes.map { it.toByte() }.toByteArray())
    fun create(pair: KeyPair, hostname: String, ip: ByteArray?): X509Certificate {
        require(hostname.matches(Regex("framerevive-[a-f0-9]{8}\\.local")))
        val algorithm = seq(oid(0x2a,0x86,0x48,0x86,0xf7,0x0d,1,1,11), der(5, byteArrayOf()))
        val name = X500Principal("CN=$hostname").encoded
        val formatter = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun date(time: Long) = der(0x18, formatter.format(Date(time)).toByteArray(Charsets.US_ASCII))
        val now = System.currentTimeMillis()
        val names = listOf(der(0x82, hostname.toByteArray(Charsets.US_ASCII)), der(0x82, "localhost".toByteArray()), der(0x87, byteArrayOf(127,0,0,1))) +
            (if (ip != null && ip.size == 4) listOf(der(0x87, ip)) else emptyList())
        val san = seq(oid(0x55,0x1d,0x11), der(4, seq(*names.toTypedArray())))
        val constraints = seq(oid(0x55,0x1d,0x13), der(1, byteArrayOf(-1)), der(4, seq()))
        val usage = seq(oid(0x55,0x1d,0x0f), der(1, byteArrayOf(-1)), der(4, der(3, byteArrayOf(5, 0xa0.toByte()))))
        val serverAuth = seq(oid(0x55,0x1d,0x25), der(4, seq(oid(0x2b,6,1,5,5,7,3,1))))
        val tbs = seq(der(0xa0, integer(BigInteger.valueOf(2))), integer(BigInteger(159, SecureRandom()).add(BigInteger.ONE)),
            algorithm, name, seq(date(now - 86_400_000L), date(now + 10L * 365 * 86_400_000L)), name, pair.public.encoded,
            der(0xa3, seq(san, constraints, usage, serverAuth)))
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(tbs); sign() }
        return CertificateFactory.getInstance("X.509").generateCertificate(seq(tbs, algorithm, der(3, byteArrayOf(0) + signature)).inputStream()) as X509Certificate
    }
}
