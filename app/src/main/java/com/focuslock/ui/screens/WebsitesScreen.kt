package com.focuslock.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.ui.FocusViewModel
import com.focuslock.ui.Route
import com.focuslock.ui.components.AppScaffold
import com.focuslock.ui.components.BoxyButton
import com.focuslock.ui.components.BoxyChip
import com.focuslock.ui.components.BoxyEmphasis
import com.focuslock.ui.components.BoxyNotice
import com.focuslock.ui.components.BoxyPanel
import com.focuslock.ui.components.NoticeTone
import com.focuslock.ui.components.ScreenHeader
import com.focuslock.ui.components.ToggleRow

/**
 * Website allowlist configuration.
 *
 * The text is intentionally explicit about what DNS filtering can and cannot do; the feature
 * is an addition to app blocking, never a replacement for it.
 */
@Composable
fun WebsitesScreen(
    vm: FocusViewModel,
    onRequestVpnConsent: () -> Unit,
) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val status by vm.enforcementStatus.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    AppScaffold {
        ScreenHeader(
            title = "Websites",
            subtitle = "Allowed domains for permitted browsers",
            onBack = { vm.navigate(Route.Home) },
        )

        BoxyPanel(title = "Allowlist") {
            ToggleRow(
                title = "Allow only these sites",
                description = "While a session runs, permitted browsers may only resolve these domains.",
                checked = draft.websiteFilterEnabled,
                onCheckedChange = vm::setWebsiteFilterEnabled,
                enabled = draft.domains.isNotEmpty(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text("rollcall.iitd.ac.in") },
                    modifier = Modifier.weight(1f),
                )
                BoxyChip(
                    label = "Add",
                    onClick = {
                        if (vm.addDomain(input)) input = ""
                    },
                )
            }
            if (draft.domains.isEmpty()) {
                Text(
                    text = "No domains yet. Subdomains of an added domain are allowed automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                draft.domains.forEach { domain ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "REMOVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { vm.removeDomain(domain) }
                                .padding(6.dp),
                        )
                    }
                }
            }
        }

        BoxyPanel(title = "Matching rules") {
            Text(
                text = "• A domain allows itself and all of its subdomains.\n" +
                    "• Adding iitd.ac.in allows rollcall.iitd.ac.in and any other *.iitd.ac.in host.\n" +
                    "• Notiitd.ac.in is a different domain and stays blocked.\n" +
                    "• Only browser apps you permit are affected; other allowed apps keep normal access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxyPanel(title = "Permission") {
            Text(
                text = if (status.vpnConsentGranted) {
                    "VPN permission granted. The filter starts automatically with a session."
                } else {
                    "Focus Lock needs the Android VPN permission to answer DNS lookups locally. " +
                        "No traffic leaves the device through a remote server."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!status.vpnConsentGranted) {
                BoxyButton(
                    label = "Grant VPN access",
                    onClick = onRequestVpnConsent,
                    emphasis = BoxyEmphasis.Outline,
                )
            }
            BoxyNotice(
                text = "Only one VPN can run at a time. Another VPN app or a system VPN will conflict.",
                tone = NoticeTone.Info,
            )
            if (!status.privateDnsOff) {
                BoxyNotice(
                    text = "Private DNS is on. It bypasses DNS filtering; turn it off in " +
                        "Network settings for the allowlist to take effect.",
                )
            }
        }

        BoxyPanel(title = "Known limits") {
            Text(
                text = "DNS filtering cannot see encrypted DNS (DoH/DoT), IP-address links, " +
                    "cached responses, or traffic from apps outside the permitted browser set. " +
                    "It is a strong speed bump, not a guarantee. App blocking remains the " +
                    "primary enforcement.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
