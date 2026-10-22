package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

class FrameRemoteTest {
    @Test fun binaryUploadIsAuthenticatedAndPreservesBytes() {
        val data = byteArrayOf(0, 1, 38, 61, -1, 0, 13, 10)
        val uploaded = AtomicReference<ByteArray>()
        FrameRemote("upload-key", { "page" }, { "unused" }, 0,
            upload = { input, length -> uploaded.set(input.readNBytes(length)); "verified" }).use { server ->
            // The callback reads exactly the declared bytes, never until HTTP connection EOF.
            fun send(key: String, length: Int = data.size): String = Socket("127.0.0.1", server.port).use { socket ->
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
        return Socket("127.0.0.1",server.port).use { socket ->
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
        FrameRemote("private-fixture-key",{"setup"},{fields->applied.set(fields);"saved"},0).use { s ->
            assertEquals(403,request(s,"/").first)
            assertEquals(200,request(s,"/?key=private-fixture-key").first)
            assertEquals(403,request(s,"/?key=private-fixture-key","action=save","http://evil.example").first)
            assertNull(applied.get())
        }
    }
    @Test fun acceptsExportLargerThanOldFormLimitAndEscapesResult() {
        val applied=AtomicReference<Map<String,String>>()
        FrameRemote("fixture-key",{"setup"},{fields->applied.set(fields);"<unsafe>"},0).use { s ->
            val session="fixture="+"x".repeat(15000)
            val result=request(s,"/?key=fixture-key","action=importSession&session="+URLEncoder.encode(session,"UTF-8"))
            assertEquals(200,result.first)
            assertEquals(session,applied.get()["session"])
            assertTrue(result.second.contains("&lt;unsafe&gt;"))
            assertFalse(result.second.contains("<unsafe>"))
        }
    }
}
