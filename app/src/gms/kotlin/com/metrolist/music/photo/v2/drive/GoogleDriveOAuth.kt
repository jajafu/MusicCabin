package com.metrolist.music.photo.v2.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The sole owner of Google OAuth SDK calls and in-memory Drive credentials. */
class GoogleDriveOAuth(private val context: Context) : DriveAuthorizationProvider {
    private val client by lazy { Identity.getAuthorizationClient(context) }
    private val requests = Mutex()
    private val lock = Any()
    private var generation = 0L
    private var email: String? = null
    private var token: String? = null

    override suspend fun authorize(accountEmail: String?, ui: DriveAuthorizationUi) = requests.withLock {
        guarded {
            val id = synchronized(lock) { generation }
            checkAvailable()
            var result = client.authorize(request(accountEmail)).awaitSdk()
            checkCurrent(id)
            if (result.hasResolution()) {
                val sender = result.pendingIntent?.intentSender ?: throw DriveException(DriveFailure.INVALID_RESPONSE)
                // Only SDK calls time out; time spent in the user's consent UI is cancellable, not timed.
                val data = ui.resolve(sender) ?: throw DriveException(DriveFailure.CANCELLED)
                checkCurrent(id)
                result = client.getAuthorizationResultFromIntent(data)
            }
            save(id, result, accountEmail)
        }
    }

    override fun bindAccount(email: String) {
        synchronized(lock) {
            check(token != null)
            this.email = email
        }
    }

    override suspend fun accessToken(forceRefresh: Boolean): String = requests.withLock {
        guarded {
            val (id, oldToken, account) = synchronized(lock) { Triple(generation, token, email) }
            if (!forceRefresh && oldToken != null) return@guarded oldToken
            if (account == null) throw DriveException(DriveFailure.REAUTHORIZE)
            checkAvailable()
            synchronized(lock) { token = null }
            if (oldToken != null) client.clearToken(ClearTokenRequest.builder().setToken(oldToken).build()).awaitSdk()
            checkCurrent(id)
            val result = client.authorize(request(account)).awaitSdk()
            checkCurrent(id)
            if (result.hasResolution()) throw DriveException(DriveFailure.REAUTHORIZE)
            save(id, result, account)
            synchronized(lock) { token ?: throw CancellationException() }
        }
    }

    override fun clear() {
        synchronized(lock) {
            generation++
            token = null
            email = null
        }
    }

    private fun request(account: String?) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
        .setOptOutIncludingGrantedScopes(true)
        .apply {
            if (account != null) setAccount(Account(account, "com.google"))
            else setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        }.build()

    private fun checkAvailable() {
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (code != ConnectionResult.SUCCESS) throw DriveException(DriveFailure.UNAVAILABLE, code)
    }

    private suspend fun checkCurrent(id: Long) {
        currentCoroutineContext().ensureActive()
        synchronized(lock) { if (id != generation) throw CancellationException() }
    }

    private suspend fun save(id: Long, result: AuthorizationResult, account: String?) {
        checkCurrent(id)
        if (DRIVE_SCOPE !in result.grantedScopes) throw DriveException(DriveFailure.PERMISSION)
        val value = result.accessToken?.takeIf { it.isNotBlank() } ?: throw DriveException(DriveFailure.REAUTHORIZE)
        synchronized(lock) {
            if (id != generation) throw CancellationException()
            token = value
            email = account
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (_: TimeoutCancellationException) {
        throw DriveException(DriveFailure.TIMEOUT)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: DriveException) {
        throw error
    } catch (error: ApiException) {
        val failure = when (error.statusCode) {
            CommonStatusCodes.CANCELED -> DriveFailure.CANCELLED
            CommonStatusCodes.TIMEOUT -> DriveFailure.TIMEOUT
            CommonStatusCodes.NETWORK_ERROR -> DriveFailure.NETWORK
            CommonStatusCodes.DEVELOPER_ERROR -> DriveFailure.CONFIGURATION
            CommonStatusCodes.API_NOT_CONNECTED -> DriveFailure.UNAVAILABLE
            else -> DriveFailure.UNKNOWN
        }
        throw DriveException(failure, error.statusCode)
    } catch (_: LinkageError) {
        throw DriveException(DriveFailure.UNAVAILABLE)
    } catch (_: Exception) {
        throw DriveException(DriveFailure.UNAVAILABLE)
    }

    private suspend fun <T> Task<T>.awaitSdk(): T = withTimeout(20_000) {
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            addOnCanceledListener { continuation.cancel() }
        }
    }

    private companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
    }
}
