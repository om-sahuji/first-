package com.focuslock.domain

/**
 * Domain matching rules for the website allowlist.
 *
 * A rule matches the host itself and every subdomain:
 *   rule `iitd.ac.in`  -> `iitd.ac.in`, `rollcall.iitd.ac.in`, `a.b.iitd.ac.in`
 *   rule `example.com` does NOT match `notexample.com`
 *
 * Normalisation accepts what people paste ("https://rollcall.iitd.ac.in/login", "www.x.org.")
 * and stores a bare, lower-case host. No wildcards, no public-suffix expansion: what you type
 * is exactly what is allowed.
 */
object DomainRules {

    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    /** Returns the canonical host, or null when the input is not a usable domain. */
    fun normalize(raw: String): String? {
        var value = raw.trim().lowercase()
        if (value.isEmpty()) return null
        value = value.substringAfter("://", value)
        value = value.substringBefore('/')
        value = value.substringBefore('?')
        value = value.substringBefore('#')
        // Drop credentials, ports and any IPv6 bracket decoration.
        if (value.contains('@')) return null
        value = value.substringAfterLast('@')
        value = value.substringBefore(':')
        value = value.trimEnd('.')
        if (value.startsWith("www.") && value.length > 4) value = value.removePrefix("www.")
        if (value.length !in 3..253) return null
        if (value.contains("..")) return null
        val labels = value.split('.')
        if (labels.size < 2) return null
        if (labels.any { !LABEL.matches(it) }) return null
        // Require a plausible top level domain; bare hostnames are not routable here.
        if (labels.last().length < 2) return null
        return value
    }

    fun matches(host: String, rule: String): Boolean {
        if (rule.isEmpty()) return false
        return host == rule || host.endsWith(".$rule")
    }

    /** Whether a DNS query name is covered by any allowlist rule. */
    fun isAllowed(host: String, rules: Collection<String>): Boolean {
        val candidate = host.trimEnd('.').lowercase()
        if (candidate.isEmpty()) return false
        return rules.any { matches(candidate, it) }
    }
}
