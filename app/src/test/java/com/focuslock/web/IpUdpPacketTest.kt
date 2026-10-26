package com.focuslock.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpUdpPacketTest {

    private val client = byteArrayOf(10, 111, 222.toByte(), 1)
    private val server = byteArrayOf(10, 111, 222.toByte(), 2)

    @Test
    fun `builds a packet that parses back to the same datagram`() {
        val payload = byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0)
        val packet = IpUdpPacket.build(
            sourceAddress = server,
            destinationAddress = client,
            sourcePort = 53,
            destinationPort = 40_000,
            payload = payload,
        )
        val parsed = IpUdpPacket.parse(packet)
        assertNotNull(parsed)
        assertEquals(53, parsed!!.sourcePort)
        assertEquals(40_000, parsed.destinationPort)
        assertTrue(parsed.payload.contentEquals(payload))
        assertTrue(parsed.sourceAddress.contentEquals(server))
        assertTrue(parsed.destinationAddress.contentEquals(client))
    }

    @Test
    fun `ipv4 header checksum is valid`() {
        val packet = IpUdpPacket.build(server, client, 53, 53, ByteArray(20) { it.toByte() })
        // A correct header checksum makes the ones-complement sum of the header zero.
        assertEquals(0, IpUdpPacket.checksum(packet, 0, IpUdpPacket.IPV4_HEADER_SIZE))
    }

    @Test
    fun `rejects non udp and truncated packets`() {
        val packet = IpUdpPacket.build(server, client, 53, 53, byteArrayOf(1, 2, 3, 4))
        val tcp = packet.copyOf()
        tcp[9] = 6
        assertNull(IpUdpPacket.parse(tcp))
        assertNull(IpUdpPacket.parse(packet.copyOf(12)))
        assertNull(IpUdpPacket.parse(ByteArray(4)))
    }

    @Test
    fun `rejects fragmented packets`() {
        val packet = IpUdpPacket.build(server, client, 53, 53, byteArrayOf(1, 2, 3, 4))
        packet[7] = 0x20 // more fragments flag
        assertNull(IpUdpPacket.parse(packet))
    }

    @Test
    fun `trims payload to the udp length`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val packet = IpUdpPacket.build(server, client, 53, 53, payload) + byteArrayOf(9, 9, 9)
        val parsed = IpUdpPacket.parse(packet)
        assertEquals(4, parsed?.payload?.size)
    }
}
