package com.metrolist.music.photo.v2.drive

import android.content.Context
import com.metrolist.music.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ViewModelComponent::class)
object DriveAuthorizationModule {
    @Provides
    fun factory(@ApplicationContext context: Context): DriveAuthorizationFactory = DriveAuthorizationFactory {
        if (BuildConfig.DRIVE_OAUTH_AVAILABLE) GoogleDriveOAuth(context) else UnavailableDriveAuthorization()
    }
}
