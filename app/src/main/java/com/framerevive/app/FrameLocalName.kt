package com.framerevive.app

import android.content.Context
import android.net.wifi.WifiManager
import java.net.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Collections

/** Minimal IPv4 mDNS hostname responder. No management key is advertised. */
class FrameLocalName(context: Context, val hostname: String) : AutoCloseable {
    @Volatile var status = "Starting local hostname…"
        private set
    @Volatile private var closed = false
    @Volatile private var socket: MulticastSocket? = null
    private val lock = context.applicationContext.getSystemService(WifiManager::class.java)
        .createMulticastLock("FrameReviveLocalName").apply { setReferenceCounted(false) }
    private val worker = Thread({
        try {
            lock.acquire()
            val network = Collections.list(NetworkInterface.getNetworkInterfaces()).firstOrNull { n ->
                n.isUp && !n.isLoopback && Collections.list(n.inetAddresses).any { it is Inet4Address && it.isSiteLocalAddress }
            } ?: error("Wi-Fi unavailable")
            val group = InetAddress.getByName("224.0.0.251")
            val s = MulticastSocket(null).apply {
                reuseAddress = true; bind(InetSocketAddress(5353)); soTimeout = 1000
                timeToLive = 255; networkInterface = network
                joinGroup(InetSocketAddress(group, 5353), network)
            }
            socket = s
            status = "Local hostname active; requires mDNS support on your network."
            while (!closed) {
                try {
                    val packet = DatagramPacket(ByteArray(1500), 1500)
                    s.receive(packet)
                    val ip = Collections.list(network.inetAddresses).firstOrNull { it is Inet4Address && it.isSiteLocalAddress } ?: continue
                    val answer = MdnsHostname.answer(packet.data.copyOf(packet.length), hostname, ip.address,
                        packet.port != 5353) ?: continue
                    val unicast = packet.port != 5353 || answer.second
                    s.send(DatagramPacket(answer.first, answer.first.size,
                        if (unicast) packet.address else group, if (unicast) packet.port else 5353))
                } catch (_: SocketTimeoutException) { }
                catch (_: IllegalArgumentException) { /* Ignore malformed multicast traffic. */ }
            }
        } catch (_: Exception) {
            if (!closed) status = "Local hostname unavailable. Use the IP link or a router DHCP reservation."
        } finally {
            socket?.close()
            if (lock.isHeld) lock.release()
        }
    }, "FrameRevive-mDNS").apply { isDaemon = true; start() }
    override fun close() { closed = true; socket?.close(); worker.interrupt() }
}

/** Bounded parser kept separate so hostile/invalid LAN packets can be unit tested. */
object MdnsHostname {
    fun answer(query: ByteArray, hostname: String, address: ByteArray, legacy: Boolean = false): Pair<ByteArray, Boolean>? {
        if (query.size < 12 || address.size != 4) return null
        fun word(at: Int): Int { require(at >= 0 && at + 1 < query.size); return ((query[at].toInt() and 255) shl 8) or (query[at+1].toInt() and 255) }
        if (word(2) and 0xF800 != 0 || word(4) !in 1..20) return null
        var cursor = 12
        var match = false; var unicast = false
        try {
            repeat(word(4)) {
                val labels = ArrayList<String>(); var at = cursor; var end = -1; var hops = 0
                while (true) {
                    require(at in query.indices && ++hops < 128)
                    val length = query[at].toInt() and 255
                    if (length == 0) { if (end < 0) end = at + 1; break }
                    if (length and 0xC0 == 0xC0) {
                        require(at + 1 < query.size)
                        if (end < 0) end = at + 2
                        at = ((length and 63) shl 8) or (query[at+1].toInt() and 255)
                    } else {
                        require(length in 1..63 && at + 1 + length <= query.size)
                        labels.add(String(query, at + 1, length, Charsets.US_ASCII)); at += length + 1
                    }
                }
                cursor = end
                val type = word(cursor); val clazz = word(cursor+2); cursor += 4
                if (labels.joinToString(".").equals(hostname, true) && type in listOf(1, 255) && clazz and 0x7FFF == 1) {
                    match = true; unicast = unicast || clazz and 0x8000 != 0
                }
            }
        } catch (_: IllegalArgumentException) { return null }
        if (!match) return null
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeShort(if (legacy) word(0) else 0); out.writeShort(0x8400)
            out.writeShort(if (legacy) word(4) else 0); out.writeShort(1); out.writeShort(0); out.writeShort(0)
            if (legacy) out.write(query, 12, cursor - 12)
            for (label in hostname.split('.')) { val b=label.toByteArray(Charsets.US_ASCII); require(b.size in 1..63); out.writeByte(b.size); out.write(b) }
            out.writeByte(0); out.writeShort(1); out.writeShort(if (legacy) 1 else 0x8001)
            out.writeInt(if (legacy) 10 else 120); out.writeShort(4); out.write(address)
        }
        return bytes.toByteArray() to unicast
    }
}
