package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

class FrameCertificateTest {
    @Test fun certificateIsSelfSignedNonCaWithStableAndUsbIdentities() {
        val cert=ManagementTlsFixture.certificate
        cert.verify(ManagementTlsFixture.pair.public)
        cert.checkValidity()
        assertEquals(-1,cert.basicConstraints)
        val names=cert.subjectAlternativeNames.map { it[1].toString() }
        assertTrue(names.contains("framerevive-0123abcd.local")); assertTrue(names.contains("127.0.0.1"))
        assertTrue(cert.keyUsage[0]); assertFalse(cert.keyUsage[5])
        assertThrows(IllegalArgumentException::class.java) { FrameCertificate.create(ManagementTlsFixture.pair,"evil.example",null) }
    }
    @Test fun browserMustTrustTheSpecificDeviceBeforeSendingHttp() {
        FrameRemote("key",{"secret"},{"saved"},ManagementTlsFixture.listener()).use { s ->
            val socket=SSLContext.getDefault().socketFactory.createSocket("127.0.0.1",s.port) as SSLSocket
            socket.use { it.soTimeout=2000; assertThrows(SSLException::class.java) { it.startHandshake() } }
        }
    }
}
