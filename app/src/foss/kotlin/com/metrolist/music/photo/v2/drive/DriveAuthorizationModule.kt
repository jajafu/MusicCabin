package com.metrolist.music.photo.v2.drive

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object DriveAuthorizationModule {
    @Provides
    fun factory(): DriveAuthorizationFactory = DriveAuthorizationFactory { UnavailableDriveAuthorization() }
}
