package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException

class FrameRemoteTest {
    @Test fun binaryUploadIsAuthenticatedAndPreservesBytes() {
        val data = byteArrayOf(0, 1, 38, 61, -1, 0, 13, 10)
        val uploaded = AtomicReference<ByteArray>()
        FrameRemote("upload-key", { "page" }, { "unused" }, ManagementTlsFixture.listener(),
            upload = { input, length -> uploaded.set(input.readNBytes(length)); "verified" }).use { server ->
            // The callback reads exactly the declared bytes, never until HTTP connection EOF.
            fun send(key: String, length: Int = data.size): String = ManagementTlsFixture.connect(server.port).use { socket ->
                socket.soTimeout = 3000
                val header = "POST /?key=$key&action=uploadApk HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\nContent-Type: application/vnd.android.package-archive\r\nContent-Length: $length\r\n\r\n"
                socket.getOutputStream().apply { write(header.toByteArray()); if(length == data.size) write(data); flush() }
                socket.shutdownOutput()
                socket.getInputStream().bufferedReader().readText()
            }
            assertTrue(send("wrong").startsWith("HTTP/1.1 403")); assertNull(uploaded.get())
            assertTrue(send("upload-key", 50_000_000).startsWith("HTTP/1.1 400")); assertNull(uploaded.get())
            assertTrue(send("upload-key").contains("verified")); assertArrayEquals(data, uploaded.get())
        }
    }
    private fun request(server: FrameRemote, path: String, body: String? = null, origin: String? = null): Pair<Int,String> {
        return ManagementTlsFixture.connect(server.port).use { socket ->
            socket.soTimeout=3000
            val bytes=body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val header=buildString {
                append(if(body==null) "GET" else "POST");append(" $path HTTP/1.1\r\nHost: 127.0.0.1:${server.port}\r\n")
                if(origin!=null)append("Origin: $origin\r\n")
                append("Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
            }
            socket.getOutputStream().apply { write(header.toByteArray());write(bytes);flush() }
            val response=socket.getInputStream().bufferedReader().readText()
            response.lineSequence().first().split(' ')[1].toInt() to response.substringAfter("\r\n\r\n")
        }
    }
    @Test fun privateKeyRequiredAndCrossOriginWritesRejected() {
        val applied=AtomicReference<Map<String,String>>()
        FrameRemote("private-fixture-key",{"setup"},{fields->applied.set(fields);"saved"},ManagementTlsFixture.listener()).use { s ->
            assertEquals(403,request(s,"/").first)
            assertEquals(200,request(s,"/?key=private-fixture-key").first)
            assertEquals(403,request(s,"/?key=private-fixture-key","action=save","http://evil.example").first)
            assertNull(applied.get())
        }
    }
    @Test fun acceptsExportLargerThanOldFormLimitAndEscapesResult() {
        val applied=AtomicReference<Map<String,String>>()
        FrameRemote("fixture-key",{"setup"},{fields->applied.set(fields);"<unsafe>"},ManagementTlsFixture.listener()).use { s ->
            val session="fixture="+"x".repeat(15000)
            val result=request(s,"/?key=fixture-key","action=importSession&session="+URLEncoder.encode(session,"UTF-8"))
            assertEquals(200,result.first)
            assertEquals(session,applied.get()["session"])
            assertTrue(result.second.contains("&lt;unsafe&gt;"))
            assertFalse(result.second.contains("<unsafe>"))
        }
    }
    @Test fun httpsOriginIsAcceptedAndHttpOriginRejected() {
        FrameRemote("key",{"page"},{"saved"},ManagementTlsFixture.listener()).use { s ->
            assertEquals(200, request(s,"/?key=key","action=save","https://127.0.0.1:${s.port}").first)
            assertEquals(403, request(s,"/?key=key","action=save","http://127.0.0.1:${s.port}").first)
        }
    }
    @Test fun plaintextAndWrongServerIdentityAreRejected() {
        FrameRemote("key",{"private-page"},{"saved"},ManagementTlsFixture.listener()).use { s ->
            Socket("127.0.0.1",s.port).use { socket ->
                socket.soTimeout=2000
                socket.getOutputStream().write("GET /?key=key HTTP/1.1\r\n\r\n".toByteArray())
                val response=runCatching { socket.getInputStream().readBytes().toString(Charsets.UTF_8) }.getOrDefault("")
                assertFalse(response.contains("private-page")); assertFalse(response.contains("HTTP/1.1 200"))
            }
            assertThrows(SSLException::class.java) { ManagementTlsFixture.connect(s.port,"other.local").close() }
            assertEquals(200,request(s,"/?key=key").first)
        }
    }
    @Test fun slowHeadersCannotBlockAnotherOwnerAndHaveAbsoluteDeadline() {
        FrameRemote("key",{"page"},{"saved"},ManagementTlsFixture.listener(),headerTimeoutMs=1000).use { s ->
            ManagementTlsFixture.connect(s.port).use { slow ->
                slow.getOutputStream().write("GET /?key=key HTTP/1.1\r\nX-Slow: ".toByteArray())
                val stopped=java.util.concurrent.atomic.AtomicBoolean(false)
                val writer=Thread { while(!stopped.get()) { if(runCatching { slow.getOutputStream().write(65); slow.getOutputStream().flush() }.isFailure) break; Thread.sleep(40) } }.apply { start() }
                try {
                    assertEquals(200,request(s,"/?key=key").first)
                    val result=runCatching { slow.getInputStream().read() }.getOrDefault(-1)
                    assertEquals(-1,result)
                } finally { stopped.set(true); writer.join(1000) }
            }
        }
    }
    @Test fun slowBodiesExpireWithoutApplyingAndServerRecovers() {
        val applied=java.util.concurrent.atomic.AtomicBoolean(false)
        FrameRemote("key",{"page"},{applied.set(true);"saved"},ManagementTlsFixture.listener(),bodyTimeoutMs=350).use { s ->
            ManagementTlsFixture.connect(s.port).use { slow ->
                slow.getOutputStream().write("POST /?key=key HTTP/1.1\r\nHost: localhost\r\nContent-Length: 1000\r\n\r\na".toByteArray())
                val writer=Thread { repeat(20) { if(runCatching { slow.getOutputStream().write(65) }.isFailure) return@Thread; Thread.sleep(40) } }.apply { start() }
                assertEquals(-1,runCatching { slow.getInputStream().read() }.getOrDefault(-1)); writer.join(1500)
                assertFalse(applied.get()); assertEquals(200,request(s,"/?key=key").first)
            }
        }
    }
    @Test fun incompleteHandshakesExpireAndShutdownClosesClients() {
        val s=FrameRemote("key",{"page"},{"saved"},ManagementTlsFixture.listener(),headerTimeoutMs=400)
        try {
            Socket("127.0.0.1",s.port).use { raw ->
                raw.soTimeout=2000
                // TLS may send a short fatal alert before EOF on the expired handshake.
                val alert=raw.getInputStream().readNBytes(1024)
                assertTrue(alert.size < 1024); assertFalse(alert.toString(Charsets.UTF_8).contains("HTTP/1.1"))
            }
            val clients=(1..4).map { Socket("127.0.0.1",s.port).apply { soTimeout=2000 } }
            try { Thread.sleep(650); assertEquals(200,request(s,"/?key=key").first) } finally { clients.forEach { it.close() } }
            ManagementTlsFixture.connect(s.port).use { client ->
                s.close(); assertEquals(-1,runCatching { client.getInputStream().read() }.getOrDefault(-1))
            }
        } finally { s.close() }
    }
}
