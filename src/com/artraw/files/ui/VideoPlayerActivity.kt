package com.artraw.files.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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

    private val handler = Looper.myLooper()?.let { Handler(it) } ?: Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private var controlsVisible = true
    private var currentFile: File? = null

    private val hideControlsRunnable = Runnable {
        hideControls()
    }

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            if (videoView.isPlaying && !isUserSeeking) {
                val current = videoView.currentPosition
                seekBar.progress = current
                tvCurrentTime.text = formatDuration(current.toLong())
            }
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
            val newPos = (videoView.currentPosition - 10000).coerceAtLeast(0)
            videoView.seekTo(newPos)
            seekBar.progress = newPos
            tvCurrentTime.text = formatDuration(newPos.toLong())
            resetControlsTimeout()
        }

        findViewById<ImageView>(R.id.btnForward10Video).setOnClickListener {
            val newPos = (videoView.currentPosition + 10000).coerceAtMost(videoView.duration)
            videoView.seekTo(newPos)
            seekBar.progress = newPos
            tvCurrentTime.text = formatDuration(newPos.toLong())
            resetControlsTimeout()
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
                    videoView.seekTo(it.progress)
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
        videoView.setVideoURI(Uri.fromFile(file))

        videoView.setOnPreparedListener { mp ->
            val duration = mp.duration
            seekBar.max = duration
            tvTotalTime.text = formatDuration(duration.toLong())
            videoView.start()
            imgPlayPause.setImageResource(R.drawable.ic_pause)
            handler.post(updateProgressRunnable)
            resetControlsTimeout()
        }

        videoView.setOnCompletionListener {
            imgPlayPause.setImageResource(R.drawable.ic_play)
            showControls()
        }

        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Unable to play video format.", Toast.LENGTH_SHORT).show()
            finish()
            true
        }
    }

    private fun togglePlayPause() {
        if (videoView.isPlaying) {
            videoView.pause()
            imgPlayPause.setImageResource(R.drawable.ic_play)
            showControls()
        } else {
            videoView.start()
            imgPlayPause.setImageResource(R.drawable.ic_pause)
            resetControlsTimeout()
        }
    }

    private fun showControls() {
        layoutControls.visibility = View.VISIBLE
        controlsVisible = true
        resetControlsTimeout()
    }

    private fun hideControls() {
        if (videoView.isPlaying) {
            layoutControls.visibility = View.GONE
            controlsVisible = false
        }
    }

    private fun resetControlsTimeout() {
        handler.removeCallbacks(hideControlsRunnable)
        if (videoView.isPlaying) {
            handler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY)
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
        videoView.stopPlayback()
        super.onDestroy()
    }
}
