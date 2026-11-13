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
import org.rustygnome.pickelhaube.tr064.InternalPhone

/**
 * The number picker's "Internal" tab: the box' phones with their internal numbers, for an internal
 * call. A tap puts the number into the dialer, with the phone's name as the field's label.
 */
@Composable
fun InternalPhonesTab(
    phones: List<InternalPhone>,
    busy: Boolean,
    onPick: (number: String, name: String) -> Unit,
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
                leadingContent = { Icon(Icons.Default.Phone, contentDescription = null) },
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("$kind · ${phone.number}", maxLines = 1) },
                modifier = Modifier.clickable { onPick(phone.number, name) },
            )
            HorizontalDivider()
        }
    }
}
