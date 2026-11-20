/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:09
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package b.ai.core.model.voice

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.k2fsa.sherpa.ncnn.RecognizerConfig
import com.k2fsa.sherpa.ncnn.SherpaNcnn
import com.k2fsa.sherpa.ncnn.getDecoderConfig
import com.k2fsa.sherpa.ncnn.getFeatureExtractorConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Sherpa-NCNN 本地识别。
 *
 * 运行时（libsherpa-ncnn-jni.so + libncnn.so）打在 jniLibs 里，模型放在 assets/sherpa；
 * 用户在设置里填了模型目录就优先用目录里的文件，方便换更大的模型而不用重装。
 *
 * 录音是 AAC/m4a，这里先解成 16k 单声道 float，再喂给识别器——
 * sherpa 的 acceptWaveform 只吃 float PCM，不认压缩格式。
 */
object SherpaAsr {

    private const val SAMPLE_RATE = 16000
    private const val FEATURE_DIM = 80
    /** assets 里的默认模型目录；Cfg.asrSherpaDir 非空时改用文件系统路径 */
    private const val ASSET_DIR = "sherpa"

    @Volatile
    private var engine: SherpaNcnn? = null
    @Volatile
    private var loadedKey: String? = null

    /** 运行时是否真的可用：类能加载且 native 库能 dlopen，缺一个都算不可用 */
    fun runtimeOk(): Boolean = runCatching {
        Class.forName("com.k2fsa.sherpa.ncnn.SherpaNcnn")
        System.loadLibrary("sherpa-ncnn-jni")
        true
    }.getOrDefault(false)

    /** 内置模型是否随包带了：assets/sherpa 下七个文件齐全（release 不打模型时为 false） */
    fun bundledModelOk(): Boolean = runCatching {
        val names = b.ai.App.self.assets.list(ASSET_DIR)?.toSet().orEmpty()
        REQUIRED.all { it in names }
    }.getOrDefault(false)

    /**
     * 这条设备上 sherpa 能不能真的用起来：运行时 +（手填目录 或 内置模型）都齐才算。
     * 设置页据此决定列不列 sherpa、显示哪种提示；聊天里选之前也用它兜底。
     */
    fun ready(dir: String = b.ai.core.cfg.app.Cfg.asrSherpaDir): Boolean {
        if (!runtimeOk()) return false
        return if (dir.isBlank()) bundledModelOk() else modelOk(dir)
    } //sherpa 可用判定

    /** 模型是否齐：目录里六个文件缺一不可 */
    fun modelOk(dir: String): Boolean {
        if (dir.isBlank()) return true //空 = 用 assets 内置模型
        val d = File(dir)
        return d.isDirectory && REQUIRED.all { File(d, it).isFile }
    }

    /** 一套模型必需的七个文件；下载器与可用性判定共用这一份清单 */
    val REQUIRED = listOf(
        "encoder_jit_trace-pnnx.ncnn.param",
        "encoder_jit_trace-pnnx.ncnn.bin",
        "decoder_jit_trace-pnnx.ncnn.param",
        "decoder_jit_trace-pnnx.ncnn.bin",
        "joiner_jit_trace-pnnx.ncnn.param",
        "joiner_jit_trace-pnnx.ncnn.bin",
        "tokens.txt"
    )

    /** 复用同一个识别器：每次重建要把 40MB 权重重新读一遍，慢且费内存 */
    private fun engine(dir: String): SherpaNcnn {
        synchronized(this) {
            val key = dir.ifBlank { ASSET_DIR }
            engine?.let { if (loadedKey == key) return it }
            runCatching { engine?.reset(true) }
            val feat = getFeatureExtractorConfig(sampleRate = SAMPLE_RATE.toFloat(), featureDim = FEATURE_DIM)
            val model = com.k2fsa.sherpa.ncnn.ModelConfig(
                encoderParam = "$key/encoder_jit_trace-pnnx.ncnn.param",
                encoderBin = "$key/encoder_jit_trace-pnnx.ncnn.bin",
                decoderParam = "$key/decoder_jit_trace-pnnx.ncnn.param",
                decoderBin = "$key/decoder_jit_trace-pnnx.ncnn.bin",
                joinerParam = "$key/joiner_jit_trace-pnnx.ncnn.param",
                joinerBin = "$key/joiner_jit_trace-pnnx.ncnn.bin",
                tokens = "$key/tokens.txt",
                numThreads = 2,
                //手机端默认关 GPU：ncnn 的 Vulkan 在这类小模型上收益有限，反而多一层驱动兼容问题
                useGPU = false
            )
            val cfg = RecognizerConfig(
                featConfig = feat,
                modelConfig = model,
                //贪心解码：短句足够，beam search 在手机上只是多耗电
                decoderConfig = getDecoderConfig(method = "greedy_search", numActivePaths = 4),
                enableEndpoint = true,
                rule1MinTrailingSilence = 2.4f,
                rule2MinTrailingSilence = 1.2f,
                rule3MinUtteranceLength = 30.0f
            )
            val e = if (dir.isBlank()) {
                SherpaNcnn(assetManager = b.ai.App.self.assets, config = cfg)
            } else {
                SherpaNcnn(config = cfg)
            }
            engine = e
            loadedKey = key
            return e
        }
    } //取得识别器

    /** 识别一整段录音；失败抛异常，由界面显示原因 */
    fun recognize(file: File, dir: String = ""): String {
        val pcm = decodePcm(file)
        if (pcm.isEmpty()) throw IllegalStateException("这段录音里没有可识别的声音")
        val e = engine(dir)
        e.reset(false)
        //按 0.1 秒一块推：一次全塞进去会把内存顶起来，分块也让 endpoint 有机会早点收尾
        val step = SAMPLE_RATE / 10
        var i = 0
        while (i < pcm.size) {
            val end = minOf(i + step, pcm.size)
            e.acceptSamples(pcm.copyOfRange(i, end))
            while (e.isReady()) e.decode()
            i = end
        }
        e.inputFinished()
        while (e.isReady()) e.decode()
        return e.text.trim()
    } //识别

    /**
     * m4a/aac -> 16k 单声道 float。
     * MediaCodec 吐出来的是 16bit PCM，先按声道取第一路，再线性重采样到 16k。
     */
    fun decodePcm(file: File): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                track = i; format = f; break
            }
        }
        if (track < 0 || format == null) {
            extractor.release()
            throw IllegalStateException("录音文件里没有音频轨道")
        }
        extractor.selectTrack(track)
        val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = ArrayList<Short>(srcRate * 30)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            if (outIdx >= 0) {
                val buf: ByteBuffer = codec.getOutputBuffer(outIdx)!!
                buf.order(ByteOrder.LITTLE_ENDIAN)
                val shorts = ShortArray(info.size / 2)
                buf.asShortBuffer().get(shorts)
                //多声道只要第一路：语音识别对声道数不敏感，混音反而把静音段拉高
                var k = 0
                while (k < shorts.size) {
                    out.add(shorts[k]); k += channels
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //输出格式变化不影响：下面按 16bit 直接读
            }
        }
        codec.stop(); codec.release(); extractor.release()

        var samples = FloatArray(out.size) { out[it] / 32768f }
        if (srcRate != SAMPLE_RATE && samples.isNotEmpty()) {
            samples = resample(samples, srcRate, SAMPLE_RATE)
        }
        return samples
    } //解码为PCM

    /** 线性重采样：语音带宽有限，线性插值足够，省一个 resampler 依赖 */
    private fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        val n = (src.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val step = from.toDouble() / to
        for (i in 0 until n) {
            val pos = i * step
            val i0 = pos.toInt().coerceIn(0, src.size - 1)
            val i1 = (i0 + 1).coerceAtMost(src.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = src[i0] * (1 - frac) + src[i1] * frac
        }
        return out
    } //重采样
} //Sherpa本地识别