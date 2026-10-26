package com.focuslock.web

import java.net.Inet4Address
import java.net.InetAddress

/**
 * The tiny slice of IPv4/UDP needed to answer DNS packets written back into the VPN tunnel.
 *
 * Only unfragmented IPv4 UDP is handled; anything else is ignored, which is safe because the
 * tunnel only routes the DNS address.
 */
data class UdpDatagram(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UdpDatagram) return false
        return sourcePort == other.sourcePort &&
            destinationPort == other.destinationPort &&
            payload.contentEquals(other.payload) &&
            sourceAddress.contentEquals(other.sourceAddress) &&
            destinationAddress.contentEquals(other.destinationAddress)
    }

    override fun hashCode(): Int {
        var result = payload.contentHashCode()
        result = 31 * result + sourcePort
        result = 31 * result + destinationPort
        result = 31 * result + sourceAddress.contentHashCode()
        result = 31 * result + destinationAddress.contentHashCode()
        return result
    }
}

object IpUdpPacket {

    const val IPV4_HEADER_SIZE = 20
    const val UDP_HEADER_SIZE = 8
    private const val PROTOCOL_UDP = 17

    fun parse(packet: ByteArray, length: Int = packet.size): UdpDatagram? {
        if (length < IPV4_HEADER_SIZE + UDP_HEADER_SIZE) return null
        val version = (packet[0].toInt() and 0xFF) shr 4
        if (version != 4) return null
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_HEADER_SIZE || length < headerLength + UDP_HEADER_SIZE) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null
        val totalLength = u16(packet, 2)
        if (totalLength < headerLength + UDP_HEADER_SIZE) return null
        // Drop fragmented packets: a DNS query or reply is always a single datagram.
        val flagsAndOffset = u16(packet, 6)
        if ((flagsAndOffset and 0x3FFF) != 0) return null

        val packetEnd = minOf(totalLength, length)
        val sourcePort = u16(packet, headerLength)
        val destinationPort = u16(packet, headerLength + 2)
        val udpLength = u16(packet, headerLength + 4)
        val payloadStart = headerLength + UDP_HEADER_SIZE
        if (payloadStart > packetEnd) return null
        val payloadEnd = minOf(packetEnd, headerLength + udpLength)
        if (payloadEnd < payloadStart) return null

        return UdpDatagram(
            sourceAddress = packet.copyOfRange(12, 16),
            destinationAddress = packet.copyOfRange(16, 20),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = packet.copyOfRange(payloadStart, payloadEnd),
        )
    }

    fun build(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        require(sourceAddress.size == 4 && destinationAddress.size == 4) { "IPv4 addresses only" }
        val udpLength = UDP_HEADER_SIZE + payload.size
        val totalLength = IPV4_HEADER_SIZE + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45 // IPv4, 5 word header
        packet[1] = 0x00 // DSCP / ECN
        putU16(packet, 2, totalLength)
        putU16(packet, 4, 0) // identification
        putU16(packet, 6, 0x4000) // don't fragment
        packet[8] = 64 // TTL
        packet[9] = PROTOCOL_UDP.toByte()
        // checksum at 10..11 written below
        sourceAddress.copyInto(packet, 12)
        destinationAddress.copyInto(packet, 16)

        putU16(packet, IPV4_HEADER_SIZE, sourcePort)
        putU16(packet, IPV4_HEADER_SIZE + 2, destinationPort)
        putU16(packet, IPV4_HEADER_SIZE + 4, udpLength)
        // A UDP checksum of zero is legal on IPv4 and avoids double work for tunnel replies.
        putU16(packet, IPV4_HEADER_SIZE + 6, 0)

        payload.copyInto(packet, IPV4_HEADER_SIZE + UDP_HEADER_SIZE)
        putU16(packet, 10, checksum(packet, 0, IPV4_HEADER_SIZE))
        return packet
    }

    fun addressBytes(address: InetAddress): ByteArray {
        val bytes = address.address
        // Normalise IPv4-mapped IPv6 addresses so the rewrite stays a 4-byte field.
        if (bytes.size == 16 && bytes.sliceArray(0 until 10).all { it == 0.toByte() } &&
            bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
        ) {
            return bytes.copyOfRange(12, 16)
        }
        return bytes
    }

    fun toAddress(bytes: ByteArray): InetAddress = InetAddress.getByAddress(bytes)

    fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += (((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)).toLong()
            index += 2
        }
        if (index < end) {
            sum += ((bytes[index].toInt() and 0xFF) shl 8).toLong()
        }
        while ((sum shr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }
}

/** Convenience for tests and diagnostics. */
fun Inet4Address.asBytes(): ByteArray = address
