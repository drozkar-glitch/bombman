@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cz.mares.bombman

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

actual object PlatformService {
    private val defaults = NSUserDefaults.standardUserDefaults
    private var activePlayer: AVAudioPlayer? = null

    actual fun loadString(key: String, default: String): String {
        return defaults.stringForKey(key) ?: default
    }

    actual fun saveString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    actual fun loadInt(key: String, default: Int): Int {
        if (defaults.objectForKey(key) == null) return default
        return defaults.integerForKey(key).toInt()
    }

    actual fun saveInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), forKey = key)
    }

    actual fun playSound(effect: SoundEffect) {
        try {
            val sampleRate = 22050
            val pcm = when (effect) {
                SoundEffect.PLACE_BOMB -> generateToneSequence(sampleRate, intArrayOf(320, 220), intArrayOf(45, 55), false)
                SoundEffect.KICK_BOMB -> generateToneSequence(sampleRate, intArrayOf(280, 420, 560), intArrayOf(35, 35, 45), false)
                SoundEffect.POWERUP -> generateToneSequence(sampleRate, intArrayOf(523, 659, 784, 1046), intArrayOf(45, 45, 45, 80), false)
                SoundEffect.ENEMY_KILL -> generateToneSequence(sampleRate, intArrayOf(440, 587, 880), intArrayOf(40, 40, 90), false)
                SoundEffect.EXPLOSION -> generateToneSequence(sampleRate, intArrayOf(140, 95, 65), intArrayOf(90, 110, 150), true)
                SoundEffect.PLAYER_DIE -> generateToneSequence(sampleRate, intArrayOf(480, 400, 320, 220, 140), intArrayOf(70, 70, 70, 90, 140), false)
                SoundEffect.LEVEL_CLEAR -> generateToneSequence(sampleRate, intArrayOf(523, 659, 784, 659, 1046), intArrayOf(70, 70, 70, 70, 180), false)
            }
            val wavBytes = buildWavBytes(sampleRate, pcm)
            val nsData = wavBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = wavBytes.size.toULong())
            }
            activePlayer = AVAudioPlayer(data = nsData, error = null)
            activePlayer?.prepareToPlay()
            activePlayer?.play()
        } catch (_: Throwable) {
        }
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
                    val tone = if (sin(2.0 * PI * f * s / sampleRate) >= 0.0) 0.6 else -0.6
                    val noise = (Random.nextDouble() * 2.0 - 1.0) * 0.7
                    (tone + noise).coerceIn(-1.0, 1.0)
                } else {
                    if (sin(2.0 * PI * f * s / sampleRate) >= 0.0) 0.8 else -0.8
                }
                out[pos++] = (wave * env * 12000).toInt().toShort()
            }
        }
        return out
    }

    private fun buildWavBytes(sampleRate: Int, pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val totalSize = 44 + dataSize
        val bytes = ByteArray(totalSize)

        fun writeAscii(offset: Int, s: String) {
            for (i in s.indices) bytes[offset + i] = s[i].code.toByte()
        }
        fun writeIntLE(offset: Int, v: Int) {
            bytes[offset] = (v and 0xFF).toByte()
            bytes[offset + 1] = ((v ushr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((v ushr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((v ushr 24) and 0xFF).toByte()
        }
        fun writeShortLE(offset: Int, v: Int) {
            bytes[offset] = (v and 0xFF).toByte()
            bytes[offset + 1] = ((v ushr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeIntLE(4, totalSize - 8)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1)
        writeShortLE(22, 1)
        writeIntLE(24, sampleRate)
        writeIntLE(28, sampleRate * 2)
        writeShortLE(32, 2)
        writeShortLE(34, 16)
        writeAscii(36, "data")
        writeIntLE(40, dataSize)

        var p = 44
        for (sample in pcm) {
            val v = sample.toInt()
            bytes[p++] = (v and 0xFF).toByte()
            bytes[p++] = ((v ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    actual fun httpGet(url: String, callback: (Boolean, String) -> Unit) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            callback(false, "Invalid URL")
            return
        }
        val req = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setHTTPMethod("GET")
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, error ->
            val httpResp = response as? NSHTTPURLResponse
            val code = httpResp?.statusCode?.toInt() ?: -1
            val body = if (data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: ""
            } else {
                error?.localizedDescription ?: ""
            }
            dispatch_async(dispatch_get_main_queue()) {
                callback(error == null && code in 200..299, body)
            }
        }
        task.resume()
    }

    actual fun httpPut(url: String, jsonBody: String, callback: (Int, String) -> Unit) {
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            callback(-1, "Invalid URL")
            return
        }
        val bodyData = (jsonBody as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        val req = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setHTTPMethod("PUT")
            setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
            setHTTPBody(bodyData)
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, error ->
            val httpResp = response as? NSHTTPURLResponse
            val code = if (error != null) -1 else (httpResp?.statusCode?.toInt() ?: -1)
            val respText = if (data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: ""
            } else {
                error?.localizedDescription ?: ""
            }
            dispatch_async(dispatch_get_main_queue()) {
                callback(code, respText)
            }
        }
        task.resume()
    }
}
