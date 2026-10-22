package com.framerevive.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.net.Socket
import java.security.*
import java.security.cert.X509Certificate
import javax.net.ssl.*

/** Stable device identity. Pair its fingerprint through the frame or authorized USB, never HTTP. */
class FrameTls(context: Context, hostname: String, ip: ByteArray?) {
    val certificate: X509Certificate
    private val key: PrivateKey
    init {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "framerevive-management-tls-v2"
        if (!store.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    // Conscrypt hashes/encodes TLS signatures itself, then asks Keystore for raw RSA.
                    // Keep this a signing-only key; no network RSA decryption cipher suites are enabled.
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .build())
            }.generateKeyPair()
        }
        key = store.getKey(alias, null) as PrivateKey
        val file = File(context.filesDir, "management-cert-v2.der")
        certificate = if (file.exists()) java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(file.inputStream().use { it.readBytes() }.inputStream()) as X509Certificate
        else FrameCertificate.create(KeyPair(store.getCertificate(alias).publicKey, key), hostname, ip).also {
            PrivatePhotosClient.writeAtomic(file, it.encoded)
        }
        // Fail closed if storage was restored or identity files do not match the non-exportable key.
        val probe = "FrameRevive identity".toByteArray()
        val signed = Signature.getInstance("SHA256withRSA").run { initSign(key); update(probe); sign() }
        check(Signature.getInstance("SHA256withRSA").run { initVerify(certificate); update(probe); verify(signed) })
        PrivatePhotosClient.writeAtomic(File(context.filesDir, "management-cert.der"), certificate.encoded)
    }
    val fingerprint: String get() = MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
    fun listen(port: Int): SSLServerSocket = FrameTlsServer.listen(certificate, key, port)
}

/** Same TLS/key-manager path used by the device and isolated JVM regression tests. */
object FrameTlsServer {
    fun listen(certificate: X509Certificate, key: PrivateKey, port: Int): SSLServerSocket {
        val manager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(type: String?, issuers: Array<out Principal>?) = null
            override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
            override fun getServerAliases(type: String?, issuers: Array<out Principal>?) = if (type == "RSA") arrayOf("frame") else null
            override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?) = if (type == "RSA") "frame" else null
            override fun getCertificateChain(alias: String?) = if (alias == "frame") arrayOf(certificate) else null
            override fun getPrivateKey(alias: String?) = if (alias == "frame") key else null
        }
        return (SSLContext.getInstance("TLS").apply { init(arrayOf(manager), null, SecureRandom()) }
            .serverSocketFactory.createServerSocket(port) as SSLServerSocket).apply {
            // Android Keystore RSA PKCS#1 signing is compatible with TLS 1.2 ECDHE_RSA.
            enabledProtocols = arrayOf("TLSv1.2")
            enabledCipherSuites = supportedCipherSuites.filter {
                it.startsWith("TLS_ECDHE_RSA_WITH_AES_") && it.endsWith("_GCM_SHA256") ||
                    it.startsWith("TLS_ECDHE_RSA_WITH_AES_") && it.endsWith("_GCM_SHA384")
            }.toTypedArray()
            check(enabledCipherSuites.isNotEmpty())
        }
    }
}
