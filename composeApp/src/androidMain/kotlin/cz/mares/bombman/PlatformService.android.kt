package cz.mares.bombman

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sin
import kotlin.random.Random

actual object PlatformService {
    private var prefs: SharedPreferences? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bombman_prefs", Context.MODE_PRIVATE)
    }

    actual fun loadString(key: String, default: String): String {
        return prefs?.getString(key, default) ?: default
    }

    actual fun saveString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    actual fun loadInt(key: String, default: Int): Int {
        return prefs?.getInt(key, default) ?: default
    }

    actual fun saveInt(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    actual fun playSound(effect: SoundEffect) {
        Thread {
            try {
                val sampleRate = 22050
                val samples: ShortArray = when (effect) {
                    SoundEffect.PLACE_BOMB -> generateToneSequence(sampleRate, intArrayOf(320, 220), intArrayOf(45, 55), false)
                    SoundEffect.KICK_BOMB -> generateToneSequence(sampleRate, intArrayOf(280, 420, 560), intArrayOf(35, 35, 45), false)
                    SoundEffect.POWERUP -> generateToneSequence(sampleRate, intArrayOf(523, 659, 784, 1046), intArrayOf(45, 45, 45, 80), false)
                    SoundEffect.ENEMY_KILL -> generateToneSequence(sampleRate, intArrayOf(440, 587, 880), intArrayOf(40, 40, 90), false)
                    SoundEffect.EXPLOSION -> generateToneSequence(sampleRate, intArrayOf(140, 95, 65), intArrayOf(90, 110, 150), true)
                    SoundEffect.PLAYER_DIE -> generateToneSequence(sampleRate, intArrayOf(480, 400, 320, 220, 140), intArrayOf(70, 70, 70, 90, 140), false)
                    SoundEffect.LEVEL_CLEAR -> generateToneSequence(sampleRate, intArrayOf(523, 659, 784, 659, 1046), intArrayOf(70, 70, 70, 70, 180), false)
                }

                val bufferSize = samples.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize.coerceAtLeast(1024))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L) / sampleRate + 80L
                Thread.sleep(durationMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
            }
        }.start()
    }

    private fun generateToneSequence(
        sampleRate: Int,
        freqs: IntArray,
        durationsMs: IntArray,
        useNoise: Boolean
    ): ShortArray {
        val totalMs = durationsMs.sum()
        val totalSamples = (sampleRate * totalMs) / 1000
        val out = ShortArray(totalSamples)
        var pos = 0
        for (idx in freqs.indices) {
            val f = freqs[idx].toDouble()
            val count = (sampleRate * durationsMs[idx]) / 1000
            for (s in 0 until count) {
                if (pos >= totalSamples) break
                val env = 1.0 - (s.toDouble() / count.toDouble()) * 0.4
                val wave = if (useNoise) {
                    val tone = if (sin(2.0 * Math.PI * f * s / sampleRate) >= 0.0) 0.6 else -0.6
                    val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.7
                    (tone + noise).coerceIn(-1.0, 1.0)
                } else {
                    if (sin(2.0 * Math.PI * f * s / sampleRate) >= 0.0) 0.8 else -0.8
                }
                out[pos++] = (wave * env * 12000).toInt().toShort()
            }
        }
        return out
    }

    actual fun httpGet(url: String, callback: (Boolean, String) -> Unit) {
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: ""
                conn.disconnect()
                mainHandler.post { callback(code in 200..299, text) }
            } catch (e: Throwable) {
                mainHandler.post { callback(false, e.message ?: "Error") }
            }
        }.start()
    }

    actual fun httpPut(url: String, jsonBody: String, callback: (Int, String) -> Unit) {
        Thread {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
                    w.write(jsonBody)
                    w.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: ""
                conn.disconnect()
                mainHandler.post { callback(code, text) }
            } catch (e: Throwable) {
                mainHandler.post { callback(-1, e.message ?: "Error") }
            }
        }.start()
    }
}
