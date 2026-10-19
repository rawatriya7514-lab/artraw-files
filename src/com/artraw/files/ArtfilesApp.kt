package com.artraw.files

import android.app.Application
import com.artraw.files.ui.ImageViewerActivity
import com.artraw.files.util.ThumbnailLoader

/**
 * Global Application class for ARTRAW Files.
 *
 * Initializes persistent cache directories, thumbnail decoders, and application-level singletons
 * at launch time so that media previews and full-resolution images load instantly across all activities.
 */
class ArtfilesApp : Application() {

    companion object {
        lateinit var instance: ArtfilesApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            // Eagerly initialize disk caches so they are available to all activities immediately
            ThumbnailLoader.init(this)
            ImageViewerActivity.initDiskCache(this)
        } catch (_: Throwable) {}
    }
}
