package com.bitchat.android.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bitchat.android.model.CallID
import com.bitchat.android.model.VoiceStreamPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentSkipListMap
import android.media.AudioTrack
import android.media.AudioManager

class VoiceCallManager(private val context: Context, private val onPacketReady: (VoiceStreamPacket) -> Unit) {

    companion object {
        private const val TAG = "VoiceCallManager"
        private const val SAMPLE_RATE = 16000 // AMR-WB sample rate
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BITRATE = 23850 // Bitrate for AMR-WB
        private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AMR_WB
        private const val JITTER_BUFFER_DELAY_MS = 100L
    }

    // Encoder
    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var recordingJob: Job? = null

    // Decoder
    private var audioTrack: AudioTrack? = null
    private var decoder: MediaCodec? = null
    private var playbackJob: Job? = null
    private val jitterBuffer = ConcurrentSkipListMap<UInt, VoiceStreamPacket>()
    private var nextSequenceToPlay: UInt = 0u

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var currentCallID: CallID? = null
    private var sequenceNumber: UInt = 0u

    fun startCall(callID: CallID) {
        if (recordingJob?.isActive == true || playbackJob?.isActive == true) {
            Log.w(TAG, "Call already in progress")
            return
        }
        currentCallID = callID
        sequenceNumber = 0u
        nextSequenceToPlay = 0u
        jitterBuffer.clear()

        startRecording()
        startPlayback()
    }

    fun stopCall() {
        stopRecording()
        stopPlayback()
        currentCallID = null
    }

    fun handleIncomingPacket(packet: VoiceStreamPacket) {
        if (packet.callID == currentCallID) {
            jitterBuffer[packet.sequenceNumber] = packet
        } else {
            Log.w(TAG, "Received packet for wrong call ID. Expected $currentCallID, got ${packet.callID}")
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            val format = MediaFormat().apply {
                setString(MediaFormat.KEY_MIME, MIME_TYPE)
                setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            audioRecord?.startRecording()
            encoder?.start()

            recordingJob = coroutineScope.launch {
                processAudioStream(bufferSize)
            }
            Log.d(TAG, "Started voice call recording for call ID: $currentCallID")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            stopRecording()
        }
    }

    private fun startPlayback() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

            val format = MediaFormat().apply {
                setString(MediaFormat.KEY_MIME, MIME_TYPE)
                setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
            }

            decoder = MediaCodec.createDecoderByType(MIME_TYPE)
            decoder?.configure(format, null, null, 0)

            audioTrack?.play()
            decoder?.start()

            playbackJob = coroutineScope.launch {
                processPlaybackStream()
            }
            Log.d(TAG, "Started voice call playback for call ID: $currentCallID")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            stopPlayback()
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            encoder?.stop()
            encoder?.release()
            encoder = null
            Log.d(TAG, "Stopped voice call recording")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            decoder?.stop()
            decoder?.release()
            decoder = null
            Log.d(TAG, "Stopped voice call playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
    }

    private suspend fun processAudioStream(bufferSize: Int) {
        val bufferInfo = MediaCodec.BufferInfo()
        val pcmBuffer = ByteArray(bufferSize)
        while (recordingJob?.isActive == true) {
            val bytesRead = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
            if (bytesRead > 0) {
                val inputBufferIndex = encoder?.dequeueInputBuffer(-1)
                if (inputBufferIndex != null && inputBufferIndex >= 0) {
                    val inputBuffer = encoder?.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(pcmBuffer, 0, bytesRead)
                    encoder?.queueInputBuffer(inputBufferIndex, 0, bytesRead, System.nanoTime() / 1000, 0)
                }
            }
            var outputBufferIndex = encoder?.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex != null && outputBufferIndex >= 0) {
                val outputBuffer = encoder?.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val encodedData = ByteArray(bufferInfo.size)
                    outputBuffer.get(encodedData)
                    val packet = VoiceStreamPacket(currentCallID ?: "unknown", sequenceNumber++, encodedData)
                    onPacketReady(packet)
                }
                encoder?.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = encoder?.dequeueOutputBuffer(bufferInfo, 0)
            }
        }
    }

    private suspend fun processPlaybackStream() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (playbackJob?.isActive == true) {
            val packet = jitterBuffer.remove(nextSequenceToPlay)
            if (packet != null) {
                nextSequenceToPlay++
                val inputBufferIndex = decoder?.dequeueInputBuffer(-1)
                if (inputBufferIndex != null && inputBufferIndex >= 0) {
                    val inputBuffer = decoder?.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(packet.audioData)
                    decoder?.queueInputBuffer(inputBufferIndex, 0, packet.audioData.size, 0, 0)
                }
            } else {
                // Wait a bit if the next packet is not yet available
                kotlinx.coroutines.delay(10)
                continue
            }

            var outputBufferIndex = decoder?.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex != null && outputBufferIndex >= 0) {
                val outputBuffer = decoder?.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val pcmData = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmData)
                    if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack?.write(pcmData, 0, pcmData.size)
                    }
                }
                decoder?.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = decoder?.dequeueOutputBuffer(bufferInfo, 0)
            }
        }
    }
}
