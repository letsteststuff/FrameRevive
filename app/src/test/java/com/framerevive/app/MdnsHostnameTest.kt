package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class MdnsHostnameTest {
    private fun query(name: String, type: Int = 1, clazz: Int = 1): ByteArray {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeShort(123); out.writeShort(0); out.writeShort(1); repeat(3){out.writeShort(0)}
            name.split('.').forEach { out.writeByte(it.length);out.writeBytes(it) }
            out.writeByte(0);out.writeShort(type);out.writeShort(clazz)
        }
        return bytes.toByteArray()
    }
    private val ip=byteArrayOf(192.toByte(),168.toByte(),1,23)
    @Test fun answersOnlyOwnIpv4Name() {
        val result=MdnsHostname.answer(query("frame-test.local"),"frame-test.local",ip)!!
        assertFalse(result.second);assertArrayEquals(ip,result.first.takeLast(4).toByteArray())
        assertNull(MdnsHostname.answer(query("other.local"),"frame-test.local",ip))
        assertNull(MdnsHostname.answer(query("frame-test.local",28),"frame-test.local",ip))
    }
    @Test fun honorsUnicastAndLegacyQueryId() {
        assertTrue(MdnsHostname.answer(query("frame.local",clazz=0x8001),"frame.local",ip)!!.second)
        val response=MdnsHostname.answer(query("frame.local"),"frame.local",ip,true)!!.first
        assertEquals(123,response[1].toInt());assertEquals(1,response[5].toInt())
    }
    @Test fun ignoresResponsesTruncationAndPointerLoops() {
        val q=query("frame.local"); q[2]=0x84.toByte()
        assertNull(MdnsHostname.answer(q,"frame.local",ip))
        assertNull(MdnsHostname.answer(query("frame.local").copyOf(14),"frame.local",ip))
        val loop=query("frame.local");loop[12]=0xC0.toByte();loop[13]=12
        assertNull(MdnsHostname.answer(loop,"frame.local",ip))
    }
}
