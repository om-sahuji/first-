package com.focuslock.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsPacketTest {

    private fun query(name: String, type: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        name.split('.').forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        out.write(byteArrayOf(0x00, type.toByte(), 0x00, 0x01))
        return out.toByteArray()
    }

    @Test
    fun `parses a normal query`() {
        val packet = DnsPacket.parseQuery(query("RollCall.iitd.ac.in"))
        assertNotNull(packet)
        assertEquals(0x1234, packet!!.id)
        assertEquals("rollcall.iitd.ac.in", packet.name)
        assertEquals(1, packet.type)
        assertEquals(packet.bytes.size, packet.questionEnd)
    }

    @Test
    fun `parses AAAA queries`() {
        val packet = DnsPacket.parseQuery(query("example.com", type = 28))
        assertEquals(28, packet?.type)
    }

    @Test
    fun `rejects truncated and compressed questions`() {
        val full = query("example.com")
        assertNull(DnsPacket.parseQuery(full.copyOf(full.size - 2)))
        assertNull(DnsPacket.parseQuery(ByteArray(4)))
        // Compression pointer inside the question section is not expected in a query.
        val compressed = ByteArray(20)
        compressed[4] = 0
        compressed[5] = 1
        compressed[12] = 0xC0.toByte()
        assertNull(DnsPacket.parseQuery(compressed))
    }

    @Test
    fun `builds an nxdomain response that echoes the question`() {
        val request = DnsPacket.parseQuery(query("blocked.example.com"))!!
        val response = DnsPacket.nxDomain(request)

        assertEquals("qrcode set", 0x80, response[2].toInt() and 0x80)
        assertEquals(DnsPacket.RCODE_NXDOMAIN, response[3].toInt() and 0x0F)
        assertEquals(1, DnsPacket.u16(response, 4)) // one question
        assertEquals(0, DnsPacket.u16(response, 6)) // no answers
        assertEquals(0, DnsPacket.u16(response, 8))
        assertEquals(0, DnsPacket.u16(response, 10))
        assertEquals(request.questionEnd, response.size)
        // Question section is byte-identical to the request.
        assertTrue(response.copyOfRange(12, response.size).contentEquals(request.bytes.copyOfRange(12, request.questionEnd)))
        // The transaction id is preserved so the resolver accepts the answer.
        assertEquals(request.id, DnsPacket.u16(response, 0))
    }

    @Test
    fun `allowlist rules drive the filter decision`() {
        assertTrue(WebsiteFilterRule.isApplicable(true, listOf("iitd.ac.in")))
        assertTrue(!WebsiteFilterRule.isApplicable(false, listOf("iitd.ac.in")))
        assertTrue(!WebsiteFilterRule.isApplicable(true, emptyList()))
        assertTrue(WebsiteFilterRule.isAllowed("rollcall.iitd.ac.in", listOf("iitd.ac.in")))
        assertTrue(!WebsiteFilterRule.isAllowed("instagram.com", listOf("iitd.ac.in")))
    }
}
