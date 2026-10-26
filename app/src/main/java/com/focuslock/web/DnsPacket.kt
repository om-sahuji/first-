package com.focuslock.web

/**
 * Minimal, allocation-cheap DNS message handling for the website allowlist.
 *
 * Only what a filtering resolver needs: read the question of a query and build a negative
 * answer. No compression pointers are accepted in the question section (queries do not use
 * them), which keeps parsing predictable: an unparseable query is simply forwarded.
 */
object DnsPacket {

    const val HEADER_SIZE = 12
    const val RCODE_NXDOMAIN = 3

    /** A parsed DNS query. [questionEnd] is the offset just past the question entry. */
    data class Query(
        val id: Int,
        val name: String,
        val type: Int,
        val bytes: ByteArray,
        val questionEnd: Int,
    )

    fun parseQuery(payload: ByteArray, length: Int = payload.size): Query? {
        if (length < HEADER_SIZE) return null
        val questionCount = u16(payload, 4)
        if (questionCount < 1) return null

        var position = HEADER_SIZE
        val nameBuilder = StringBuilder()
        var labels = 0
        while (position < length) {
            val labelLength = payload[position].toInt() and 0xFF
            if (labelLength == 0) {
                position += 1
                break
            }
            if (labelLength and 0xC0 == 0xC0) return null
            if (labelLength > 63) return null
            if (position + 1 + labelLength > length) return null
            if (nameBuilder.isNotEmpty()) nameBuilder.append('.')
            nameBuilder.append(String(payload, position + 1, labelLength, Charsets.US_ASCII))
            position += labelLength + 1
            labels += 1
            if (labels > 127) return null
        }
        if (labels == 0) return null
        if (position + 4 > length) return null
        val name = nameBuilder.toString().trimEnd('.').lowercase()
        if (name.isEmpty()) return null
        val type = u16(payload, position)
        return Query(
            id = u16(payload, 0),
            name = name,
            type = type,
            bytes = payload.copyOfRange(0, position + 4),
            questionEnd = position + 4,
        )
    }

    /**
     * Builds an NXDOMAIN response for [query], echoing the question so the client can match
     * the answer to its request. Blocked lookups therefore fail fast instead of hanging.
     */
    fun nxDomain(query: Query): ByteArray {
        val response = query.bytes.copyOfRange(0, query.questionEnd)
        require(response.size >= HEADER_SIZE)
        // QR = 1
        response[2] = (response[2].toInt() or 0x80).toByte()
        // RA = 1, RCODE = 3 (NXDOMAIN); keep RD as the client sent it.
        response[3] = (((response[3].toInt() and 0xF0) or 0x80) or RCODE_NXDOMAIN).toByte()
        // Exactly one question, no records.
        writeU16(response, 4, 1)
        writeU16(response, 6, 0)
        writeU16(response, 8, 0)
        writeU16(response, 10, 0)
        return response
    }

    fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }
}
