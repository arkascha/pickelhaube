package org.rustygnome.pickelhaube.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.rustygnome.pickelhaube.R
import org.rustygnome.pickelhaube.tr064.FritzDialer
import org.rustygnome.pickelhaube.tr064.InternalPhone

/**
 * The number picker's "Internal" tab: the box' phones with their internal numbers, for an internal
 * call. A tap puts the number into the dialer, with the phone's name as the field's label; its call
 * button calls it right away.
 */
@Composable
fun InternalPhonesTab(
    phones: List<InternalPhone>,
    busy: Boolean,
    onPick: (number: String, name: String) -> Unit,
    /** Calls a phone right away (number, name); null: no call buttons (picking for a widget). */
    onCall: ((number: String, name: String) -> Unit)?,
    /** A box to call through is there: the call buttons work. */
    callEnabled: Boolean,
    /** The phone set under "Ring phone" (as the box names it): it cannot call itself, so its button is off. */
    ringPhone: String?,
) {
    if (phones.isEmpty()) {
        if (busy) Message(stringResource(R.string.internal_loading), MascotPose.SEARCHING)
        else Message(stringResource(R.string.internal_none), MascotPose.PUZZLED)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState()) {
        items(phones, key = { it.number }) { phone ->
            val name = phone.name.ifEmpty { stringResource(R.string.internal_all_phones) }
            val kind = stringResource(
                when (phone.kind) {
                    InternalPhone.Kind.ANALOGUE -> R.string.internal_kind_analogue
                    InternalPhone.Kind.DECT -> R.string.internal_kind_dect
                    InternalPhone.Kind.IP -> R.string.internal_kind_ip
                    InternalPhone.Kind.ALL -> R.string.internal_kind_all
                },
            )
            ListItem(
                // The phone under "Ring phone" rings first and then calls this one – not itself.
                leadingContent = if (onCall != null) {
                    {
                        val itself = ringPhone != null && phone.name.isNotEmpty() &&
                            FritzDialer.displayName(ringPhone).equals(phone.name, ignoreCase = true)
                        CallButton(phone.number, name, enabled = callEnabled && !itself) { onCall(phone.number, name) }
                    }
                } else {
                    { Icon(Icons.Default.Phone, contentDescription = null) }
                },
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("$kind · ${phone.number}", maxLines = 1) },
                modifier = Modifier.clickable { onPick(phone.number, name) },
            )
            HorizontalDivider()
        }
    }
}
