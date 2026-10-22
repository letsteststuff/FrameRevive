package com.framerevive.app

import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.net.ssl.*

/** Trust only the generated fixture identity; exercise real TLS and hostname validation. */
object ManagementTlsFixture {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val certificate = FrameCertificate.create(pair, "framerevive-0123abcd.local", null)
    private val clientContext = SSLContext.getInstance("TLS").apply {
        val trust = KeyStore.getInstance("PKCS12").apply { load(null, null); setCertificateEntry("fixture", certificate) }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        init(null, managers.trustManagers, null)
    }
    fun listener() = FrameTlsServer.listen(certificate, pair.private, 0)
    fun connect(port: Int, hostname: String = "127.0.0.1"): SSLSocket {
        val socket = clientContext.socketFactory.createSocket(Socket("127.0.0.1", port), hostname, port, true) as SSLSocket
        socket.soTimeout = 2500
        socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        try { socket.startHandshake(); return socket } catch (e: Exception) { socket.close(); throw e }
    }
}
