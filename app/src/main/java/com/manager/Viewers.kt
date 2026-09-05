package com.manager

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ViewerHost(viewer: ViewerState, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (viewer) {
            is ViewerState.Image -> ImageViewer(viewer.path, onClose)
            is ViewerState.Media -> MediaPlayerScreen(viewer.path, viewer.isVideo, onClose)
        }
    }
}

@Composable
fun ImageViewer(path: String, onClose: () -> Unit) {
    val bitmap = remember(path) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale <= 1f) Offset.Zero else offset + pan
                        }
                    }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Не удалось открыть изображение",
                    color = Color.White
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Закрыть",
                tint = Color.White
            )
        }
    }
}

@Composable
fun MediaPlayerScreen(path: String, isVideo: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current

    val mediaPlayer = remember { MediaPlayer() }

    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(1) }
    var position by remember { mutableStateOf(0) }
    var hasPrepared by remember { mutableStateOf(false) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var volume by remember {
        mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }

    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()

    fun prepare(holder: SurfaceHolder?) {
        if (hasPrepared) return
        hasPrepared = true

        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(path)

            @Suppress("DEPRECATION")
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

            if (holder != null) {
                mediaPlayer.setDisplay(holder)
            }

            mediaPlayer.setOnPreparedListener { mp ->
                prepared = true
                duration = if (mp.duration > 0) mp.duration else 1
                mp.start()
                playing = true
            }

            mediaPlayer.setOnCompletionListener {
                playing = false
            }

            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            hasPrepared = false
        }
    }

    DisposableEffect(path) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
            } catch (_: Exception) {
            }

            mediaPlayer.release()
        }
    }

    LaunchedEffect(prepared, playing) {
        while (prepared && playing) {
            try {
                position = mediaPlayer.currentPosition.coerceAtLeast(0)
            } catch (_: Exception) {
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isVideo) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                prepare(holder)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LaunchedEffect(Unit) {
                prepare(null)
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(position.toLong()),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )

                Slider(
                    value = position.toFloat(),
                    onValueChange = { newValue ->
                        if (prepared) {
                            position = newValue.toInt()
                            try {
                                mediaPlayer.seekTo(newValue.toInt())
                            } catch (_: Exception) {
                            }
                        }
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    enabled = prepared
                )

                Text(
                    text = formatTime(duration.toLong()),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (!prepared) return@IconButton

                    try {
                        if (playing) {
                            mediaPlayer.pause()
                            playing = false
                        } else {
                            mediaPlayer.start()
                            playing = true
                        }
                    } catch (_: Exception) {
                    }
                }) {
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )

                Slider(
                    value = volume,
                    onValueChange = { v ->
                        volume = v
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            v.toInt(),
                            0
                        )
                    },
                    valueRange = 0f..maxVolume,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Закрыть",
                tint = Color.White
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
