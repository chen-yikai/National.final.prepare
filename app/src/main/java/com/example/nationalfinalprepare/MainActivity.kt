package com.example.nationalfinalprepare

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.Audio.Media
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.nationalfinalprepare.ui.theme.NationalfinalprepareTheme
import java.lang.Exception

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NationalfinalprepareTheme {
//                Entry()
                VideoPlayer()
            }
        }
    }
}

@Composable
fun VideoPlayer() {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val videoUrl =
        "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    var isLoading by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableFloatStateOf(16f / 10f) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .widthIn(max = 700.dp)
                .aspectRatio(aspectRatio)
        ) {
            AndroidView(factory = {
                SurfaceView(it).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            player = MediaPlayer().apply {
                                setDataSource(context, Uri.parse(videoUrl))
                                setDisplay(holder)
                                setOnInfoListener { mp, what, extra ->
                                    when (what) {
                                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                                            isLoading = true
                                        }

                                        MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                                            isLoading = false
                                        }

                                        MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                                            isLoading = false
                                        }
                                    }
                                    true
                                }
                                setOnPreparedListener { mp ->
                                    //                                val width = it.videoWidth.toFloat()
                                    //                                val height = it.videoHeight.toFloat()
                                    //                                if (height > 0 && width > 0) {
                                    //                                    aspectRatio = width / height
                                    //                                }
                                    mp.start()
                                }
                                prepareAsync()
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            try {
                                player?.release()
                                player = null
                            } catch (e: Exception) {

                            }
                        }

                    })
                }
            })
        }
    }
}

@Composable
fun Entry() {
    var context = LocalContext.current
    var recordingLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val intent = Intent(context, RecordingService::class.java).apply {
                putExtra("resultCode", it.resultCode)
                putExtra("data", it.data)
            }
            context.startForegroundService(intent)
        }
    var permissionRequestLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val audioRecordPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val notificationPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    LaunchedEffect(Unit) {
        if (!audioRecordPermission) permissionRequestLauncher.launch(Manifest.permission.RECORD_AUDIO)
        if (!notificationPermission) permissionRequestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(floatingActionButton = {
        Icon(Icons.Default.Add, contentDescription = null)
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                val intent = mediaProjectionManager.createScreenCaptureIntent()
                recordingLauncher.launch(intent)
            }) {
                Text("Start Recording")
            }
            Button(onClick = {
                context.stopService(Intent(context, RecordingService::class.java))
            }) { Text("Stop Recording") }
        }
    }
}