/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.photo.FramePhotoQrCode

/**
 * TV account page: shows login status, and when logged out, a QR code plus a
 * 6-digit pairing code so the phone app can push its session over the LAN.
 */
@Composable
fun TvAuthPage(
    modifier: Modifier = Modifier,
    viewModel: TvAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(state.loggedIn) {
        if (!state.loggedIn) viewModel.startReceiver()
        onDispose { viewModel.stopReceiver() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TvHeading(tvLocalizedString(
            com.metrolist.music.R.string.tv_account, com.metrolist.music.R.string.tv_account_zh_tw)) }
        if (state.loggedIn) {
            item {
                Text(
                    text = tvLocalizedString(
                        com.metrolist.music.R.string.tv_auth_signed_in,
                        com.metrolist.music.R.string.tv_auth_signed_in_zh_tw, state.accountName),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (state.justAuthorized != null) {
                item { TvMessage(tvLocalizedString(
                    com.metrolist.music.R.string.tv_auth_syncing,
                    com.metrolist.music.R.string.tv_auth_syncing_zh_tw)) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(tvLocalizedString(
                        com.metrolist.music.R.string.tv_auth_sync_now,
                        com.metrolist.music.R.string.tv_auth_sync_now_zh_tw), false) {
                        viewModel.syncNow()
                    }
                    TvButton(tvLocalizedString(
                        com.metrolist.music.R.string.tv_auth_logout,
                        com.metrolist.music.R.string.tv_auth_logout_zh_tw), false) {
                        viewModel.logout()
                    }
                }
            }
        } else {
            when {
                state.starting -> item { TvMessage(tvLocalizedString(
                    com.metrolist.music.R.string.tv_photo_pair_waiting,
                    com.metrolist.music.R.string.tv_photo_pair_waiting_zh_tw)) }
                state.startError -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvMessage(tvLocalizedString(
                            com.metrolist.music.R.string.tv_photo_pair_error,
                            com.metrolist.music.R.string.tv_photo_pair_error_zh_tw))
                        TvButton(androidx.compose.ui.res.stringResource(com.metrolist.music.R.string.retry), false) {
                            viewModel.startReceiver()
                        }
                    }
                }
                state.url != null -> {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            FramePhotoQrCode(state.url!!, tvLocalizedString(
                                com.metrolist.music.R.string.tv_auth_qr_hint,
                                com.metrolist.music.R.string.tv_auth_qr_hint_zh_tw))
                        }
                    }
                    item {
                        Text(
                            text = state.code.chunked(3).joinToString(" "),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold, letterSpacing = 8.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    item {
                        Text(
                            text = tvLocalizedString(
                                com.metrolist.music.R.string.tv_auth_address,
                                com.metrolist.music.R.string.tv_auth_address_zh_tw, state.url!!),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    item { TvMessage(tvLocalizedString(
                        com.metrolist.music.R.string.tv_auth_hint,
                        com.metrolist.music.R.string.tv_auth_hint_zh_tw)) }
                }
            }
        }
    }
}
