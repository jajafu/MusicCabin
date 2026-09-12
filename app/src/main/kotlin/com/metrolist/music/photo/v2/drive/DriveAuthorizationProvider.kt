package com.metrolist.music.photo.v2.drive

import android.content.Intent
import android.content.IntentSender

/** Android activity-result types only; the Google SDK stays in the GMS implementation. */
fun interface DriveAuthorizationUi {
    suspend fun resolve(sender: IntentSender): Intent?
}

fun interface DriveAuthorizationFactory {
    fun create(): DriveAuthorizationProvider
}

interface DriveAuthorizationProvider {
    suspend fun authorize(accountEmail: String?, ui: DriveAuthorizationUi)
    fun bindAccount(email: String)
    suspend fun accessToken(forceRefresh: Boolean = false): String
    fun clear()
}

enum class DriveFailure {
    DISABLED, UNAVAILABLE, CANCELLED, TIMEOUT, REAUTHORIZE, CONFIGURATION,
    NETWORK, PERMISSION, NOT_FOUND, DOWNLOAD_FORBIDDEN, RATE_LIMITED, SERVER,
    TOO_LARGE, INVALID_IMAGE, STORAGE, INVALID_RESPONSE, UNKNOWN,
}

/** Do not retain SDK/HTTP exceptions: their messages may contain credentials or private URLs. */
class DriveException(val failure: DriveFailure, val code: Int? = null) : Exception(failure.name)

class UnavailableDriveAuthorization : DriveAuthorizationProvider {
    override suspend fun authorize(accountEmail: String?, ui: DriveAuthorizationUi): Unit = unavailable()
    override fun bindAccount(email: String) = Unit
    override suspend fun accessToken(forceRefresh: Boolean): String = unavailable()
    override fun clear() = Unit
    private fun unavailable(): Nothing = throw DriveException(DriveFailure.UNAVAILABLE)
}
