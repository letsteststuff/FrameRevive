package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

class FrameRemoteTest {
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
