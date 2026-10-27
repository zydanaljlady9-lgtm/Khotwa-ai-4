package com.khotwa.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * ذكاء داخل الهاتف نفسه (Gemini Nano عبر ML Kit / AICore).
 * مجاني، خاص، يعمل بلا إنترنت، ويرى الصور على الهواتف التي تدعمه (Pixel 9+، Galaxy S25+ وغيرها).
 * على الهواتف غير المدعومة يبقى معطّلاً بصمت ويعمل خُطوة بالمحركات الأخرى.
 */
object OnDeviceAi {
    enum class State { CHECKING, UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, READY }

    var state by mutableStateOf(State.CHECKING)
        private set
    var downloadedMb by mutableStateOf(0L)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var model: GenerativeModel? = null
    private var checked = false

    val ready: Boolean get() = state == State.READY

    private fun client(): GenerativeModel? {
        model?.let { return it }
        return try {
            Generation.getClient().also { model = it }
        } catch (_: Throwable) {
            null
        }
    }

    /** يفحص توفّر النموذج مرة عند بدء التطبيق (لا يحمّل شيئاً دون طلب المستخدم). */
    fun refresh(force: Boolean = false) {
        if (checked && !force) return
        checked = true
        scope.launch {
            val m = client()
            if (m == null) {
                state = State.UNAVAILABLE
                return@launch
            }
            state = try {
                when (withTimeout(8000) { m.checkStatus() }) {
                    FeatureStatus.AVAILABLE -> State.READY
                    FeatureStatus.DOWNLOADABLE -> State.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> State.DOWNLOADING
                    else -> State.UNAVAILABLE
                }
            } catch (_: Throwable) {
                State.UNAVAILABLE
            }
            if (state == State.READY) {
                try {
                    m.warmup()
                } catch (_: Throwable) {
                }
            }
        }
    }

    /** تنزيل النموذج داخل الهاتف بطلب المستخدم (عبر خدمات Google، مرة واحدة). */
    fun download() {
        val m = client() ?: return
        if (state != State.DOWNLOADABLE && state != State.DOWNLOADING) return
        state = State.DOWNLOADING
        scope.launch {
            try {
                m.download().collect { s ->
                    when (s) {
                        is DownloadStatus.DownloadProgress -> downloadedMb = s.totalBytesDownloaded / (1024 * 1024)
                        is DownloadStatus.DownloadCompleted -> state = State.READY
                        is DownloadStatus.DownloadFailed -> state = State.DOWNLOADABLE
                        else -> {}
                    }
                }
                if (state == State.DOWNLOADING) refresh(true)
            } catch (_: Throwable) {
                state = State.DOWNLOADABLE
            }
        }
    }

    /**
     * يولّد رداً كاملاً (يُستدعى من خيط خلفي). onDelta يتلقى النص المتراكم أثناء البث.
     * image بصيغة JPEG base64 اختيارياً.
     */
    fun generate(
        prompt: String,
        imageB64: String?,
        maxTokens: Int,
        onDelta: ((String) -> Unit)?,
        cancelled: () -> Boolean
    ): String {
        val m = client() ?: throw IllegalStateException("on-device unavailable")
        if (!ready) throw IllegalStateException("on-device not ready")
        val bmp: Bitmap? = imageB64?.let {
            try {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }
        }
        val req = if (bmp != null) {
            generateContentRequest(ImagePart(bmp), TextPart(prompt)) {
                temperature = 0.2f
                maxOutputTokens = maxTokens
            }
        } else {
            generateContentRequest(TextPart(prompt)) {
                temperature = 0.2f
                maxOutputTokens = maxTokens
            }
        }
        return runBlocking {
            withTimeout(30000) {
                val sb = StringBuilder()
                try {
                    m.generateContentStream(req).collect { chunk ->
                        if (cancelled()) throw StopSignal()
                        val t = chunk.candidates.firstOrNull()?.text.orEmpty()
                        if (t.isNotEmpty()) {
                            sb.append(t)
                            onDelta?.invoke(sb.toString())
                        }
                    }
                } catch (_: StopSignal) {
                }
                sb.toString()
            }
        }
    }

    private class StopSignal : RuntimeException()
}
