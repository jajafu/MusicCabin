package com.metrolist.music.photo.v2

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.photo.v2.drive.DriveFailure

// Paired resources keep this removable experiment within the project's permitted strings file.
enum class FrameV2Text(@StringRes val english: Int, @StringRes val traditionalChinese: Int) {
    SettingsSubtitle(R.string.photo_frame_v2_settings_subtitle, R.string.photo_frame_v2_settings_subtitle_zh_tw),
    DriveSource(R.string.photo_frame_v2_drive_source, R.string.photo_frame_v2_drive_source_zh_tw),
    LocalSourceDetail(R.string.photo_frame_v2_local_source_detail, R.string.photo_frame_v2_local_source_detail_zh_tw),
    CloudSourceDetail(R.string.photo_frame_v2_cloud_source_detail, R.string.photo_frame_v2_cloud_source_detail_zh_tw),
    SelectedPhotos(R.string.photo_frame_v2_selected_photos, R.string.photo_frame_v2_selected_photos_zh_tw),
    FolderSelection(R.string.photo_frame_v2_folder_selection, R.string.photo_frame_v2_folder_selection_zh_tw),
    DriveDetails(R.string.photo_frame_v2_drive_details, R.string.photo_frame_v2_drive_details_zh_tw),
    FormatAndCache(R.string.photo_frame_v2_format_and_cache, R.string.photo_frame_v2_format_and_cache_zh_tw),
    Expanded(R.string.photo_frame_v2_expanded, R.string.photo_frame_v2_expanded_zh_tw),
    Collapsed(R.string.photo_frame_v2_collapsed, R.string.photo_frame_v2_collapsed_zh_tw),
    NotConnected(R.string.photo_frame_v2_not_connected, R.string.photo_frame_v2_not_connected_zh_tw),
    Source(R.string.photo_frame_v2_source, R.string.photo_frame_v2_source_zh_tw),
    LocalPhotos(R.string.photo_frame_v2_local_photos, R.string.photo_frame_v2_local_photos_zh_tw),
    CloudFolder(R.string.photo_frame_v2_cloud_folder, R.string.photo_frame_v2_cloud_folder_zh_tw),
    ChooseSource(R.string.photo_frame_v2_choose_source, R.string.photo_frame_v2_choose_source_zh_tw),
    ToggleControls(R.string.photo_frame_v2_toggle_controls, R.string.photo_frame_v2_toggle_controls_zh_tw),
    Preparing(R.string.photo_frame_v2_preparing, R.string.photo_frame_v2_preparing_zh_tw),
    NoPlayablePhotos(R.string.photo_frame_v2_no_playable_photos, R.string.photo_frame_v2_no_playable_photos_zh_tw),
    FromCache(R.string.photo_frame_v2_from_cache, R.string.photo_frame_v2_from_cache_zh_tw),
    LoadedFromDrive(R.string.photo_frame_v2_loaded_from_drive, R.string.photo_frame_v2_loaded_from_drive_zh_tw),
    SlideshowCount(R.string.photo_frame_v2_slideshow_count, R.string.photo_frame_v2_slideshow_count_zh_tw),
    PauseSlideshow(R.string.photo_frame_v2_pause_slideshow, R.string.photo_frame_v2_pause_slideshow_zh_tw),
    ResumeSlideshow(R.string.photo_frame_v2_resume_slideshow, R.string.photo_frame_v2_resume_slideshow_zh_tw),
    NextPhoto(R.string.photo_frame_v2_next_photo, R.string.photo_frame_v2_next_photo_zh_tw),
    ChangeFolder(R.string.photo_frame_v2_change_folder, R.string.photo_frame_v2_change_folder_zh_tw),
    Interval(R.string.photo_frame_v2_interval, R.string.photo_frame_v2_interval_zh_tw),
    Seconds(R.string.photo_frame_v2_seconds, R.string.photo_frame_v2_seconds_zh_tw),
    Title(R.string.photo_frame_v2_title, R.string.photo_frame_v2_title_zh_tw),
    EntryDescription(R.string.photo_frame_v2_entry_description, R.string.photo_frame_v2_entry_description_zh_tw),
    Scope(R.string.photo_frame_v2_scope, R.string.photo_frame_v2_scope_zh_tw),
    Enable(R.string.photo_frame_v2_enable, R.string.photo_frame_v2_enable_zh_tw),
    Permission(R.string.photo_frame_v2_permission, R.string.photo_frame_v2_permission_zh_tw),
    BuildDisabled(R.string.photo_frame_v2_build_disabled, R.string.photo_frame_v2_build_disabled_zh_tw),
    Disabled(R.string.photo_frame_v2_disabled, R.string.photo_frame_v2_disabled_zh_tw),
    Account(R.string.photo_frame_v2_account, R.string.photo_frame_v2_account_zh_tw),
    SavedAccount(R.string.photo_frame_v2_saved_account, R.string.photo_frame_v2_saved_account_zh_tw),
    Connect(R.string.photo_frame_v2_connect, R.string.photo_frame_v2_connect_zh_tw),
    Disconnect(R.string.photo_frame_v2_disconnect, R.string.photo_frame_v2_disconnect_zh_tw),
    DisconnectHint(R.string.photo_frame_v2_disconnect_hint, R.string.photo_frame_v2_disconnect_hint_zh_tw),
    Cancel(R.string.photo_frame_v2_cancel, R.string.photo_frame_v2_cancel_zh_tw),
    RefreshToken(R.string.photo_frame_v2_refresh_token, R.string.photo_frame_v2_refresh_token_zh_tw),
    Ready(R.string.photo_frame_v2_ready, R.string.photo_frame_v2_ready_zh_tw),
    Authorizing(R.string.photo_frame_v2_authorizing, R.string.photo_frame_v2_authorizing_zh_tw),
    Connected(R.string.photo_frame_v2_connected, R.string.photo_frame_v2_connected_zh_tw),
    Listing(R.string.photo_frame_v2_listing, R.string.photo_frame_v2_listing_zh_tw),
    Listed(R.string.photo_frame_v2_listed, R.string.photo_frame_v2_listed_zh_tw),
    Downloading(R.string.photo_frame_v2_downloading, R.string.photo_frame_v2_downloading_zh_tw),
    Previewed(R.string.photo_frame_v2_previewed, R.string.photo_frame_v2_previewed_zh_tw),
    Refreshed(R.string.photo_frame_v2_refreshed, R.string.photo_frame_v2_refreshed_zh_tw),
    Stopped(R.string.photo_frame_v2_stopped, R.string.photo_frame_v2_stopped_zh_tw),
    Root(R.string.photo_frame_v2_root, R.string.photo_frame_v2_root_zh_tw),
    Parent(R.string.photo_frame_v2_parent, R.string.photo_frame_v2_parent_zh_tw),
    RefreshFolders(R.string.photo_frame_v2_refresh_folders, R.string.photo_frame_v2_refresh_folders_zh_tw),
    ListPhotos(R.string.photo_frame_v2_list_photos, R.string.photo_frame_v2_list_photos_zh_tw),
    NoFolders(R.string.photo_frame_v2_no_folders, R.string.photo_frame_v2_no_folders_zh_tw),
    PhotoCount(R.string.photo_frame_v2_photo_count, R.string.photo_frame_v2_photo_count_zh_tw),
    PreviewLimit(R.string.photo_frame_v2_preview_limit, R.string.photo_frame_v2_preview_limit_zh_tw),
    CannotPreview(R.string.photo_frame_v2_cannot_preview, R.string.photo_frame_v2_cannot_preview_zh_tw),
    Preview(R.string.photo_frame_v2_preview, R.string.photo_frame_v2_preview_zh_tw),
    Back(R.string.photo_frame_v2_back, R.string.photo_frame_v2_back_zh_tw),
    Unavailable(R.string.photo_frame_v2_unavailable, R.string.photo_frame_v2_unavailable_zh_tw),
    Cancelled(R.string.photo_frame_v2_cancelled, R.string.photo_frame_v2_cancelled_zh_tw),
    Timeout(R.string.photo_frame_v2_timeout, R.string.photo_frame_v2_timeout_zh_tw),
    Reauthorize(R.string.photo_frame_v2_reauthorize, R.string.photo_frame_v2_reauthorize_zh_tw),
    Configuration(R.string.photo_frame_v2_configuration, R.string.photo_frame_v2_configuration_zh_tw),
    Network(R.string.photo_frame_v2_network, R.string.photo_frame_v2_network_zh_tw),
    PermissionDenied(R.string.photo_frame_v2_permission_denied, R.string.photo_frame_v2_permission_denied_zh_tw),
    NotFound(R.string.photo_frame_v2_not_found, R.string.photo_frame_v2_not_found_zh_tw),
    DownloadForbidden(R.string.photo_frame_v2_download_forbidden, R.string.photo_frame_v2_download_forbidden_zh_tw),
    RateLimited(R.string.photo_frame_v2_rate_limited, R.string.photo_frame_v2_rate_limited_zh_tw),
    Server(R.string.photo_frame_v2_server, R.string.photo_frame_v2_server_zh_tw),
    TooLarge(R.string.photo_frame_v2_too_large, R.string.photo_frame_v2_too_large_zh_tw),
    InvalidImage(R.string.photo_frame_v2_invalid_image, R.string.photo_frame_v2_invalid_image_zh_tw),
    Storage(R.string.photo_frame_v2_storage, R.string.photo_frame_v2_storage_zh_tw),
    InvalidResponse(R.string.photo_frame_v2_invalid_response, R.string.photo_frame_v2_invalid_response_zh_tw),
    Unknown(R.string.photo_frame_v2_unknown, R.string.photo_frame_v2_unknown_zh_tw),
}

@Composable
fun frameV2String(text: FrameV2Text, vararg args: Any): String {
    val chinese = LocalConfiguration.current.locales[0].language == "zh"
    return stringResource(if (chinese) text.traditionalChinese else text.english, *args)
}

internal fun DriveFailure.text(): FrameV2Text = when (this) {
    DriveFailure.DISABLED -> FrameV2Text.Disabled
    DriveFailure.UNAVAILABLE -> FrameV2Text.Unavailable
    DriveFailure.CANCELLED -> FrameV2Text.Cancelled
    DriveFailure.TIMEOUT -> FrameV2Text.Timeout
    DriveFailure.REAUTHORIZE -> FrameV2Text.Reauthorize
    DriveFailure.CONFIGURATION -> FrameV2Text.Configuration
    DriveFailure.NETWORK -> FrameV2Text.Network
    DriveFailure.PERMISSION -> FrameV2Text.PermissionDenied
    DriveFailure.NOT_FOUND -> FrameV2Text.NotFound
    DriveFailure.DOWNLOAD_FORBIDDEN -> FrameV2Text.DownloadForbidden
    DriveFailure.RATE_LIMITED -> FrameV2Text.RateLimited
    DriveFailure.SERVER -> FrameV2Text.Server
    DriveFailure.TOO_LARGE -> FrameV2Text.TooLarge
    DriveFailure.INVALID_IMAGE -> FrameV2Text.InvalidImage
    DriveFailure.STORAGE -> FrameV2Text.Storage
    DriveFailure.INVALID_RESPONSE -> FrameV2Text.InvalidResponse
    DriveFailure.UNKNOWN -> FrameV2Text.Unknown
}
