package com.example.bible.ui.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bible.R
import com.example.bible.data.travel.FriendPeerLocation

/**
 * Лист при тапе по метке «локация другого пользователя»: координаты и удаление с карты.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendPeerMarkerTapSheet(
    snapshot: FriendPeerLocation?,
    liveLocation: FriendPeerLocation?,
    onDismiss: () -> Unit,
    onRemoveFromMap: () -> Unit,
) {
    if (snapshot == null) return

    LaunchedEffect(liveLocation) {
        if (liveLocation == null) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        ) {
            Text(
                snapshot.label?.trim()?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.travel_friend_peer_pin_default),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.travel_friend_peer_current_fmt,
                    snapshot.latitude,
                    snapshot.longitude,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.travel_friend_peer_map_sheet_remove_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.travel_cancel))
                }
                TextButton(
                    onClick = {
                        onRemoveFromMap()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.travel_friend_peer_map_sheet_remove))
                }
            }
        }
    }
}
