package com.privacyhub.app.ui.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Best-effort generated white noise; no audio asset or extra permission. Off by default. */
class WhiteNoisePlayer {
    private var track: AudioTrack? = null
    private var job: Job? = null

    fun start() {
        if (track != null) return
        try {
            val rate = 22050
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val size = if (min > 0) min else rate / 2
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            track = t
            job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val buf = ByteArray(size)
                val rnd = Random(System.nanoTime())
                while (isActive) {
                    for (i in buf.indices) buf[i] = rnd.nextInt(256).toByte()
                    try { t.write(buf, 0, buf.size) } catch (_: Exception) { break }
                }
            }
        } catch (_: Exception) { stop() }
    }

    fun stop() {
        job?.cancel(); job = null
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
    }
}
