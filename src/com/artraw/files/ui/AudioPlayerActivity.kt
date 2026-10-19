package com.artraw.files.ui

import android.app.Activity
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.util.MimeTypes
import com.artraw.files.util.ThumbnailLoader
import java.io.File
import java.util.Locale

class AudioPlayerActivity : Activity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        private val metadataCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()
    }

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    private lateinit var tvTrackTitle: TextView
    private lateinit var tvTrackArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var imgPlayPause: ImageView
    private lateinit var btnPlayPause: FrameLayout
    private lateinit var imgAlbumArt: ImageView

    private var currentFile: File? = null
    private var playlist = mutableListOf<File>()
    private var currentIndex = 0

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (!isUserSeeking && mp.isPlaying) {
                    val current = mp.currentPosition
                    seekBar.progress = current
                    tvCurrentTime.text = formatDuration(current.toLong())
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        val root = findViewById<android.view.View>(R.id.rootAudioPlayer)
        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerAudio)),
            bottomScrollViews = listOf(root)
        )

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path == null) {
            Toast.makeText(this, "Audio file not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentFile = file
        buildPlaylist(file)

        initViews()
        setupListeners()
        playTrack(file)
    }

    private fun initViews() {
        tvTrackTitle = findViewById(R.id.tvTrackTitle)
        tvTrackArtist = findViewById(R.id.tvTrackArtist)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        seekBar = findViewById(R.id.seekBarAudio)
        imgPlayPause = findViewById(R.id.imgPlayPause)
        btnPlayPause = findViewById(R.id.btnPlayPauseAudio)
        imgAlbumArt = findViewById(R.id.imgAlbumArt)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnBackAudio).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.btnShareAudio).setOnClickListener {
            currentFile?.let { f ->
                val item = com.artraw.files.model.FileItem(
                    file = f,
                    name = f.name,
                    size = f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = false,
                    extension = MimeTypes.getExtension(f.name),
                    mimeType = MimeTypes.getMimeType(f),
                    category = com.artraw.files.model.FileCategory.AUDIO
                )
                FileViewerHelper.shareFiles(this, listOf(item))
            }
        }

        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }

        findViewById<ImageView>(R.id.btnReplay10).setOnClickListener {
            mediaPlayer?.let { mp ->
                val newPos = (mp.currentPosition - 10000).coerceAtLeast(0)
                mp.seekTo(newPos)
                seekBar.progress = newPos
                tvCurrentTime.text = formatDuration(newPos.toLong())
            }
        }

        findViewById<ImageView>(R.id.btnForward10).setOnClickListener {
            mediaPlayer?.let { mp ->
                val newPos = (mp.currentPosition + 10000).coerceAtMost(mp.duration)
                mp.seekTo(newPos)
                seekBar.progress = newPos
                tvCurrentTime.text = formatDuration(newPos.toLong())
            }
        }

        findViewById<ImageView>(R.id.btnNextTrack).setOnClickListener {
            playNextTrack()
        }

        findViewById<ImageView>(R.id.btnPrevTrack).setOnClickListener {
            playPrevTrack()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                sb?.let {
                    mediaPlayer?.seekTo(it.progress)
                }
                isUserSeeking = false
            }
        })
    }

    private fun buildPlaylist(initialFile: File) {
        val parent = initialFile.parentFile ?: return
        val audioFiles = parent.listFiles { f ->
            !f.isDirectory && MimeTypes.getCategory(f) == com.artraw.files.model.FileCategory.AUDIO
        }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()

        playlist.clear()
        playlist.addAll(audioFiles)
        currentIndex = playlist.indexOfFirst { it.absolutePath == initialFile.absolutePath }
        if (currentIndex == -1 && playlist.isNotEmpty()) currentIndex = 0
    }

    private fun playTrack(file: File) {
        currentFile = file

        val cachedMeta = metadataCache[file.absolutePath]
        var trackTitle = cachedMeta?.first ?: file.nameWithoutExtension
        var trackArtist = cachedMeta?.second ?: (file.parentFile?.name ?: "Storage")

        tvTrackTitle.text = trackTitle
        tvTrackArtist.text = trackArtist

        if (cachedMeta == null) {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    if (!metaTitle.isNullOrBlank()) trackTitle = metaTitle
                    if (!metaArtist.isNullOrBlank()) trackArtist = metaArtist
                    metadataCache[file.absolutePath] = Pair(trackTitle, trackArtist)
                    tvTrackTitle.text = trackTitle
                    tvTrackArtist.text = trackArtist
                } finally {
                    try { retriever.release() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }

        val audioItem = com.artraw.files.model.FileItem(
            file = file,
            name = file.name,
            size = file.length(),
            lastModified = file.lastModified(),
            isDirectory = false,
            extension = MimeTypes.getExtension(file.name),
            mimeType = MimeTypes.getMimeType(file),
            category = com.artraw.files.model.FileCategory.AUDIO
        )
        ThumbnailLoader.loadThumbnail(
            imageView = imgAlbumArt,
            item = audioItem,
            targetSize = 320,
            defaultIconRes = R.drawable.ic_audio
        )

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(this@AudioPlayerActivity, Uri.fromFile(file))
                prepare()
                start()
            }

            val duration = mediaPlayer?.duration ?: 0
            seekBar.max = duration
            seekBar.progress = 0
            tvCurrentTime.text = "0:00"
            tvTotalTime.text = formatDuration(duration.toLong())
            imgPlayPause.setImageResource(R.drawable.ic_pause)

            mediaPlayer?.setOnCompletionListener {
                playNextTrack()
            }

            handler.removeCallbacks(updateProgressRunnable)
            handler.post(updateProgressRunnable)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play: ${e.message}", Toast.LENGTH_SHORT).show()
            imgPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            imgPlayPause.setImageResource(R.drawable.ic_play)
        } else {
            mp.start()
            imgPlayPause.setImageResource(R.drawable.ic_pause)
        }
    }

    private fun playNextTrack() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playTrack(playlist[currentIndex])
    }

    private fun playPrevTrack() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playTrack(playlist[currentIndex])
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateProgressRunnable)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
