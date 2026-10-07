/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeAuthUserKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.tv.TvAuthReceiver
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.InfoLabel
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Pushes this phone's YouTube session to a TV on the same LAN.
 * The TV account page shows its address and a 6-digit pairing code; both are
 * typed here because typing on the phone is cheap and on the TV is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorizeTvScreen(
    navController: NavController,
    initialAddress: String = "",
) {
    val scope = rememberCoroutineScope()
    val (cookie) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId) = rememberPreference(DataSyncIdKey, "")
    val (authUser) = rememberPreference(InnerTubeAuthUserKey, "0")
    val (accountName) = rememberPreference(AccountNameKey, "")
    val (accountEmail) = rememberPreference(AccountEmailKey, "")
    val (channelHandle) = rememberPreference(AccountChannelHandleKey, "")

    var tvAddress by rememberSaveable(initialAddress) { mutableStateOf(initialAddress) }
    var pairCode by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val sentOk = tvAuthString(R.string.tv_authorize_sent, R.string.tv_authorize_sent_zh_tw)
    val sentFail = tvAuthString(R.string.tv_authorize_failed, R.string.tv_authorize_failed_zh_tw)
    val lockedMessage = tvAuthString(R.string.tv_authorize_locked, R.string.tv_authorize_locked_zh_tw)
    val networkError = tvAuthString(R.string.tv_authorize_network_error, R.string.tv_authorize_network_error_zh_tw)
    val loginFirst = tvAuthString(R.string.tv_authorize_login_first, R.string.tv_authorize_login_first_zh_tw)

    val isLoggedIn = "SAPISID" in parseCookieString(cookie)
    val codeValid = pairCode.trim().length == 6 && pairCode.trim().all(Char::isDigit)
    val target = normalizeTvTarget(tvAddress)

    fun send() {
        val url = target ?: return
        sending = true
        result = null
        scope.launch(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("code", pairCode.trim())
                put("cookie", cookie)
                put("visitorData", visitorData)
                put("dataSyncId", dataSyncId)
                put("authUser", authUser)
                put("accountName", accountName)
                put("accountEmail", accountEmail)
                put("channelHandle", channelHandle)
            }.toString()
            val message = runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> sentOk
                        429 -> lockedMessage
                        else -> "$sentFail (${response.code})"
                    }
                }
            }.getOrElse {
                reportException(it)
                networkError
            }
            withContext(Dispatchers.Main) {
                sending = false
                result = message
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(tvAuthString(R.string.tv_authorize_title, R.string.tv_authorize_title_zh_tw)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isLoggedIn) {
                InfoLabel(text = loginFirst)
                Button(
                    onClick = { navController.navigate("login") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tvAuthString(R.string.tv_authorize_go_login, R.string.tv_authorize_go_login_zh_tw))
                }
            } else {
                Text(
                    text = tvAuthString(R.string.tv_authorize_account, R.string.tv_authorize_account_zh_tw, accountName.ifBlank { "YouTube" }),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            InfoLabel(text = tvAuthString(R.string.tv_authorize_desc, R.string.tv_authorize_desc_zh_tw))
            OutlinedTextField(
                value = tvAddress,
                onValueChange = { tvAddress = it },
                label = { Text(tvAuthString(R.string.tv_authorize_address_hint, R.string.tv_authorize_address_hint_zh_tw)) },
                placeholder = { Text("192.168.1.10:${TvAuthReceiver.PORT}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pairCode,
                onValueChange = { pairCode = it.filter(Char::isDigit).take(6) },
                label = { Text(tvAuthString(R.string.tv_authorize_code_hint, R.string.tv_authorize_code_hint_zh_tw)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = ::send,
                enabled = isLoggedIn && target != null && codeValid && !sending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (sending) tvAuthString(R.string.tv_authorize_sending, R.string.tv_authorize_sending_zh_tw)
                    else tvAuthString(R.string.tv_authorize_send, R.string.tv_authorize_send_zh_tw),
                )
            }
            result?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun tvAuthString(english: Int, chinese: Int, vararg args: Any): String {
    val id = if (LocalConfiguration.current.locales[0].language == "zh") chinese else english
    return if (args.isEmpty()) stringResource(id) else stringResource(id, *args)
}

private fun normalizeTvTarget(raw: String): String? {
    var host = raw.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore("/")
        .trim()
    if (host.isEmpty() || host.any(Char::isWhitespace)) return null
    if (!host.contains(":")) host += ":${TvAuthReceiver.PORT}"
    return "http://$host/auth"
}
