package b.ai.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Q 弹音效：现场合成一段下滑正弦，不占包体积、没有版权问题，想换真音效再说 */
object PetSound {
    private const val RATE = 44100

    /** 放一声「Q弹」，vol 取 0..1 */
    fun boing(vol: Float) {
        if (vol <= 0f) return
        Thread { runCatching { play(vol) } }.apply { isDaemon = true }.start()
    } //对外放音

    private fun play(vol: Float) {
        val n = (RATE * 0.18f).toInt()
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            //频率从 900Hz 指数滑到 180Hz，就是弹回来那一下的手感
            val f = 900.0 * Math.exp(-9.0 * t) + 180.0
            phase += 2.0 * Math.PI * f / RATE
            val env = Math.exp(-14.0 * t)
            val s = (Math.sin(phase) * 0.72 + Math.sin(phase * 2.0) * 0.24) * env * vol
            buf[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        val at = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        at.write(buf, 0, n)
        at.play()
        Thread.sleep(320)
        runCatching { at.stop() }
        at.release()
    } //合成并播放
} //桌宠音效