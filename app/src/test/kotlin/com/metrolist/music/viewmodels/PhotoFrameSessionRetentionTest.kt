package com.metrolist.music.viewmodels

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.photo.PhotoCatalog
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PhotoFrameSessionRetentionTest {
    @Test
    fun `rotation recomposition reuses the retained session`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = PhotoFrameViewModel(context, PhotoCatalog(context))
        val uris = listOf("content://media/1", "content://media/2", "content://media/3")
        val session = viewModel.playbackSession(uris, generation = 0)
        // Rotation rebuilds the composition with identical inputs: the same
        // session must come back so playback resumes at the current photo.
        assertSame(session, viewModel.playbackSession(uris, generation = 0))
    }

    @Test
    fun `changed photo list or generation rebuilds the session`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = PhotoFrameViewModel(context, PhotoCatalog(context))
        val uris = listOf("content://media/1", "content://media/2", "content://media/3")
        val session = viewModel.playbackSession(uris, generation = 0)
        assertNotSame(session, viewModel.playbackSession(uris, generation = 1))
        assertNotSame(
            viewModel.playbackSession(uris, generation = 1),
            viewModel.playbackSession(uris.dropLast(1), generation = 1),
        )
    }
}
