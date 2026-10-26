package com.focuslock.web

import com.focuslock.domain.DomainRules

/**
 * Decides whether a DNS lookup may proceed while the allowlist is active.
 *
 * While active, the rule is strictly allow-listed: only configured domains and their
 * subdomains resolve. When no domain is configured the filter refuses to run at all
 * (see the VPN service), because silently breaking all name resolution would take permitted
 * apps offline.
 */
object WebsiteFilterRule {

    fun isApplicable(enabled: Boolean, domains: Collection<String>): Boolean =
        enabled && domains.isNotEmpty()

    fun isAllowed(host: String, domains: Collection<String>): Boolean =
        DomainRules.isAllowed(host, domains)
}
