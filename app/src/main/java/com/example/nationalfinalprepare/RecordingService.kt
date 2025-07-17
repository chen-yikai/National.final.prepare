package com.example.nationalfinalprepare

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Exception
import java.util.UUID
import kotlin.math.min

class RecordingService() : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, recording_channel_name)
            .setContentTitle("Recording Service").setContentText("Recording in progress...")
            .setSmallIcon(R.drawable.ic_launcher_foreground).build()
        startForeground(1, notification)
        val resultCode =
            intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        if (resultCode == Activity.RESULT_OK && data != null) {
            val mediaProjectionManager =
                this.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopRecording()
                }
            }, null)
            startRecording()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private var mediaProjection: MediaProjection? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var muxer: MediaMuxer? = null
    private var job: Job? = null
    private var muxerStarted: Boolean = false
    private var audioTrackIndex: Int = -1
    private var videoTrackIndex: Int = -1

    private fun startRecording() {
        val id = UUID.randomUUID().toString()
        val outputFile = File(getExternalFilesDir(null), "$id.mp4")
        val screen = resources.displayMetrics
        val width = screen.widthPixels
        val height = screen.heightPixels
        val dpi = screen.densityDpi

        val videoFormat = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, width * height * 5)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        videoEncoder = MediaCodec.createEncoderByType("video/avc").apply {
            configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        val inputSurface = videoEncoder?.createInputSurface()
        videoEncoder?.start()

        val audioFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
        }

        audioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm").apply {
            configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val audioRecordFormat =
            AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
        val audioRecordMaxSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            audioRecord = AudioRecord.Builder().setAudioFormat(audioRecordFormat)
                .setAudioPlaybackCaptureConfig(config).setBufferSizeInBytes(audioRecordMaxSize)
                .build()
        } else {
            stopSelf()
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RecordingScreen",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null
        )

        audioRecord?.startRecording()

        job = CoroutineScope(Dispatchers.IO).launch {
            val videoInfo = MediaCodec.BufferInfo()
            val audioInfo = MediaCodec.BufferInfo()
            val audioBuffer = ByteArray(audioRecordMaxSize)

            while (true) {
               try {
                    val videoOutputIndex = videoEncoder?.dequeueOutputBuffer(videoInfo, 10000)!!
                    if (videoOutputIndex >= 0) {
                        val videoData = videoEncoder?.getOutputBuffer(videoOutputIndex)!!
                        if ((videoInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (!muxerStarted) {
                                videoTrackIndex = muxer!!.addTrack(videoEncoder!!.outputFormat)
                                audioTrackIndex = muxer!!.addTrack(audioEncoder!!.outputFormat)
                                muxer!!.start()
                                muxerStarted = true
                            }
                            muxer!!.writeSampleData(videoTrackIndex, videoData, videoInfo)
                        }
                        videoEncoder!!.releaseOutputBuffer(videoOutputIndex, false)
                    }

                    val read = audioRecord?.read(audioBuffer, 0, audioRecordMaxSize)!!
                    if (read > 0) {
                        val audioInputIndex = audioEncoder?.dequeueInputBuffer(10000)!!
                        if (audioInputIndex >= 0) {
                            val audioInput = audioEncoder?.getInputBuffer(audioInputIndex)!!
                            audioInput.clear()
                            val len = min(audioInput.capacity(), read)
                            audioInput.put(audioBuffer)
                            audioEncoder!!.queueInputBuffer(
                                audioInputIndex,
                                0,
                                len,
                                System.nanoTime() / 1000,
                                0
                            )
                        }
                    }

                    var audioOutputIndex = audioEncoder!!.dequeueOutputBuffer(audioInfo, 0)
                    while (audioOutputIndex >= 0) {
                        val audioOutputData = audioEncoder!!.getOutputBuffer(audioOutputIndex)!!
                        if ((audioInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && muxerStarted) {
                            muxer!!.writeSampleData(audioTrackIndex, audioOutputData, audioInfo)
                        }
                        audioEncoder!!.releaseOutputBuffer(audioOutputIndex, false)
                        audioOutputIndex = audioEncoder!!.dequeueOutputBuffer(audioInfo, 0)
                    }
                }catch(e:Exception){

                }
            }
        }
    }

    private fun stopRecording() {
        try {
            job?.cancel()

            audioRecord?.stop()
            audioRecord?.release()

            videoEncoder?.stop()
            videoEncoder?.release()

            audioEncoder?.stop()
            audioEncoder?.release()

            mediaProjection?.stop()

            virtualDisplay?.release()

            muxer?.stop()
            muxer?.release()
        } catch (e: Exception) {

        }finally {
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}