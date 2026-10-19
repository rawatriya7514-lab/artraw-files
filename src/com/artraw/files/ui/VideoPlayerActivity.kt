package com.artraw.files.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import com.artraw.files.R
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.util.MimeTypes
import java.io.File
import java.util.Locale

class VideoPlayerActivity : Activity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        private const val HIDE_CONTROLS_DELAY = 3500L
    }

    private lateinit var videoView: VideoView
    private lateinit var layoutControls: RelativeLayout
    private lateinit var tvTitle: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var imgPlayPause: ImageView
    private lateinit var btnPlayPause: FrameLayout
    private lateinit var imgVideoCover: ImageView
    private lateinit var progressVideoLoading: ProgressBar

    private val handler = Looper.myLooper()?.let { Handler(it) } ?: Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private var controlsVisible = true
    private var currentFile: File? = null

    private fun isPlayingSafely(): Boolean {
        return try {
            videoView.isPlaying
        } catch (_: Throwable) {
            false
        }
    }

    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            try {
                if (isPlayingSafely() && !isUserSeeking) {
                    val current = videoView.currentPosition
                    seekBar.progress = current
                    tvCurrentTime.text = formatDuration(current.toLong())
                }
            } catch (_: Throwable) {}
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path == null) {
            Toast.makeText(this, "Video not found.", Toast.LENGTH_SHORT).show()
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
        initViews()
        setupListeners()
        loadVideo(file)
    }

    private fun initViews() {
        videoView = findViewById(R.id.videoView)
        layoutControls = findViewById(R.id.layoutVideoControls)
        tvTitle = findViewById(R.id.tvVideoTitle)
        tvCurrentTime = findViewById(R.id.tvVideoCurrentTime)
        tvTotalTime = findViewById(R.id.tvVideoTotalTime)
        seekBar = findViewById(R.id.seekBarVideo)
        imgPlayPause = findViewById(R.id.imgPlayPauseVideo)
        btnPlayPause = findViewById(R.id.btnPlayPauseVideo)
        imgVideoCover = findViewById(R.id.imgVideoCover)
        progressVideoLoading = findViewById(R.id.progressVideoLoading)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnBackVideo).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.btnShareVideo).setOnClickListener {
            currentFile?.let { f ->
                val item = com.artraw.files.model.FileItem(
                    file = f,
                    name = f.name,
                    size = f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = false,
                    extension = MimeTypes.getExtension(f.name),
                    mimeType = MimeTypes.getMimeType(f),
                    category = com.artraw.files.model.FileCategory.VIDEOS
                )
                FileViewerHelper.shareFiles(this, listOf(item))
            }
        }

        btnPlayPause.setOnClickListener {
            togglePlayPause()
            resetControlsTimeout()
        }

        findViewById<ImageView>(R.id.btnReplay10Video).setOnClickListener {
            try {
                val newPos = (videoView.currentPosition - 10000).coerceAtLeast(0)
                videoView.seekTo(newPos)
                seekBar.progress = newPos
                tvCurrentTime.text = formatDuration(newPos.toLong())
                resetControlsTimeout()
            } catch (_: Throwable) {}
        }

        findViewById<ImageView>(R.id.btnForward10Video).setOnClickListener {
            try {
                val maxDur = if (videoView.duration > 0) videoView.duration else Int.MAX_VALUE
                val newPos = (videoView.currentPosition + 10000).coerceAtMost(maxDur)
                videoView.seekTo(newPos)
                seekBar.progress = newPos
                tvCurrentTime.text = formatDuration(newPos.toLong())
                resetControlsTimeout()
            } catch (_: Throwable) {}
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isUserSeeking = true
                handler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                sb?.let {
                    try {
                        videoView.seekTo(it.progress)
                    } catch (_: Throwable) {}
                }
                isUserSeeking = false
                resetControlsTimeout()
            }
        })

        // Tap root to toggle controls
        findViewById<FrameLayout>(R.id.rootVideoPlayer).setOnClickListener {
            if (controlsVisible) {
                hideControls()
            } else {
                showControls()
            }
        }

        layoutControls.setOnClickListener {
            if (controlsVisible) {
                hideControls()
            }
        }
    }

    private fun loadVideo(file: File) {
        tvTitle.text = file.name

        // Fast preview: show cached video thumbnail immediately in 0ms!
        val cached = com.artraw.files.util.ThumbnailLoader.getCachedBitmap(file.absolutePath)
        if (cached != null && !cached.isRecycled) {
            imgVideoCover.setImageBitmap(cached)
            imgVideoCover.visibility = View.VISIBLE
        }
        progressVideoLoading.visibility = View.VISIBLE

        try {
            // Direct path avoids provider IPC permission denial from mediaserver
            videoView.setVideoPath(file.absolutePath)
        } catch (_: Throwable) {
            try {
                videoView.setVideoURI(Uri.fromFile(file))
            } catch (t: Throwable) {
                progressVideoLoading.visibility = View.GONE
                Toast.makeText(this, "Cannot play video: ${t.message}", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        videoView.setOnPreparedListener { mp ->
            if (isFinishing || isDestroyed) return@setOnPreparedListener
            try {
                // Video is ready: dismiss loading and cover immediately
                imgVideoCover.visibility = View.GONE
                progressVideoLoading.visibility = View.GONE

                val duration = mp.duration
                seekBar.max = duration
                tvTotalTime.text = formatDuration(duration.toLong())
                videoView.start()
                imgPlayPause.setImageResource(R.drawable.ic_pause)
                handler.post(updateProgressRunnable)
                resetControlsTimeout()
            } catch (_: Throwable) {}
        }

        videoView.setOnCompletionListener {
            if (isFinishing || isDestroyed) return@setOnCompletionListener
            imgPlayPause.setImageResource(R.drawable.ic_play)
            showControls()
        }

        videoView.setOnErrorListener { _, _, _ ->
            if (!isFinishing && !isDestroyed) {
                imgVideoCover.visibility = View.GONE
                progressVideoLoading.visibility = View.GONE
                Toast.makeText(this, "Unable to play video format.", Toast.LENGTH_SHORT).show()
                finish()
            }
            true
        }
    }

    private fun togglePlayPause() {
        try {
            if (isPlayingSafely()) {
                videoView.pause()
                imgPlayPause.setImageResource(R.drawable.ic_play)
                showControls()
            } else {
                videoView.start()
                imgPlayPause.setImageResource(R.drawable.ic_pause)
                resetControlsTimeout()
            }
        } catch (_: Throwable) {}
    }

    private fun showControls() {
        layoutControls.visibility = View.VISIBLE
        controlsVisible = true
        resetControlsTimeout()
    }

    private fun hideControls() {
        if (isPlayingSafely()) {
            layoutControls.visibility = View.GONE
            controlsVisible = false
        }
    }

    private fun resetControlsTimeout() {
        handler.removeCallbacks(hideControlsRunnable)
        if (isPlayingSafely()) {
            handler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY)
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    override fun onPause() {
        super.onPause()
        try {
            if (isPlayingSafely()) {
                videoView.pause()
                imgPlayPause.setImageResource(R.drawable.ic_play)
            }
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
        try {
            videoView.stopPlayback()
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
