package b.ai.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import b.ai.R

/**
 * Q 弹音效：优先用打包的 pet_boing.wav（SoundPool，走媒体音量流），
 * 采样还没加载好或者资源缺失时现场合成一段，保证一定有声。
 */
object PetSound {
    private var pool: SoundPool? = null
    private var sid = 0
    private var ready = false
    private var tried = false

    @Synchronized
    private fun ensure(c: Context): Boolean {
        if (tried) return ready
        tried = true
        return runCatching {
            val p = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            //小采样常同步加载完，回调会早于 sid 赋值，所以不能拿 id == sid 判
            p.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) ready = true
            }
            sid = p.load(c.applicationContext, R.raw.pet_boing, 1)
            pool = p
            true
        }.getOrDefault(false)
    } //懒加载音频

    /** 提前把采样加载好，免第一次点击走合成 */
    fun warm(c: Context) {
        runCatching { ensure(c) }
    } //预热

    /** 放一声「Q弹」，vol 取 0..1；没给 Context 就只走合成 */
    fun boing(c: Context?, vol: Float) {
        if (vol <= 0f) return
        val v = vol.coerceIn(0f, 1f)
        if (c != null && runCatching { ensure(c) }.getOrDefault(false)) {
            val p = pool
            if (p != null && ready && runCatching { p.play(sid, v, v, 1, 0, 1f) }.getOrDefault(0) > 0) return
        }
        synth(v)
    } //对外放音

    private fun synth(vol: Float) {
        val rate = 44100
        val n = (rate * 0.2f).toInt()
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / rate
            //频率从 1150Hz 指数滑到 250Hz，就是弹回来那一下的手感
            val f = 1150.0 * Math.exp(-13.0 * t) + 250.0
            phase += 2.0 * Math.PI * f / rate
            val env = Math.exp(-10.5 * t) * (1.0 - Math.exp(-400.0 * t))
            val s = (Math.sin(phase) * 0.78 + Math.sin(phase * 2.0) * 0.26 + Math.sin(phase * 3.0) * 0.09) * env * vol * 0.85
            buf[i] = (s * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
        //播放丢到子线程：MODE_STATIC 一次写满再播，别在点击回调里 sleep 卡界面
        Thread {
            runCatching {
                val at = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                at.write(buf, 0, n)
                at.play()
                Thread.sleep(240)
                runCatching { at.stop() }
                at.release()
            }
        }.start()
    } //合成兜底
} //桌宠音效