package com.focuslock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRulesTest {

    @Test
    fun `normalises what users paste`() {
        assertEquals("rollcall.iitd.ac.in", DomainRules.normalize("rollcall.iitd.ac.in"))
        assertEquals("rollcall.iitd.ac.in", DomainRules.normalize("  HTTPS://Rollcall.IITD.ac.in/login?x=1 "))
        assertEquals("rollcall.iitd.ac.in", DomainRules.normalize("rollcall.iitd.ac.in."))
        assertEquals("iitd.ac.in", DomainRules.normalize("www.iitd.ac.in"))
        assertEquals("example.com", DomainRules.normalize("example.com:8443"))
    }

    @Test
    fun `rejects unusable input`() {
        assertNull(DomainRules.normalize(""))
        assertNull(DomainRules.normalize("not a domain"))
        assertNull(DomainRules.normalize("localhost"))
        assertNull(DomainRules.normalize("-bad.example.com"))
        assertNull(DomainRules.normalize("user@example.com"))
    }

    @Test
    fun `matches the domain and its subdomains only`() {
        val rule = "iitd.ac.in"
        assertTrue(DomainRules.matches("iitd.ac.in", rule))
        assertTrue(DomainRules.matches("rollcall.iitd.ac.in", rule))
        assertTrue(DomainRules.matches("a.b.iitd.ac.in", rule))
        assertFalse(DomainRules.matches("notiitd.ac.in", rule))
        assertFalse(DomainRules.matches("iitd.ac.in.evil.com", rule))
        assertFalse(DomainRules.matches("ac.in", rule))
    }

    @Test
    fun `allowlist is case and trailing-dot tolerant`() {
        assertTrue(DomainRules.isAllowed("RollCall.IITD.ac.in.", listOf("iitd.ac.in")))
        assertFalse(DomainRules.isAllowed("example.com", listOf("iitd.ac.in")))
        assertFalse(DomainRules.isAllowed("anything.example", emptyList()))
    }
}
