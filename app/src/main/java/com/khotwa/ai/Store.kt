package com.khotwa.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/** إجراء يظهر كبطاقة داخل المحادثة: إذن، فتح رابط، بدء الإرشاد. */
data class Action(
    val type: String,
    val label: String = "",
    val url: String = "",
    val goal: String = ""
)

data class Msg(
    val id: Long,
    val role: String,
    val text: String,
    val actions: List<Action> = emptyList(),
    val engine: String = "",
    val hasImage: Boolean = false
)

data class Conv(val id: Long, val title: String, val msgs: List<Msg>, val updated: Long)

/** حساب المستخدم محلياً على الهاتف (Google أو ضيف). */
object Account {
    var onboarded by mutableStateOf(false)
    var signedIn by mutableStateOf(false)
    var name by mutableStateOf("")
    var email by mutableStateOf("")

    fun load(c: Context) {
        val s = Prefs.sp(c)
        onboarded = s.getBoolean("onboarded", false)
        signedIn = s.getBoolean("signedIn", false)
        name = s.getString("name", "") ?: ""
        email = s.getString("email", "") ?: ""
    }

    fun setGoogle(c: Context, n: String, e: String) {
        name = n
        email = e
        signedIn = true
        onboarded = true
        Prefs.setConsent(c, true)
        save(c)
    }

    fun setGuest(c: Context) {
        name = ""
        email = ""
        signedIn = false
        onboarded = true
        Prefs.setConsent(c, true)
        save(c)
    }

    fun signOut(c: Context) {
        name = ""
        email = ""
        signedIn = false
        onboarded = false
        save(c)
    }

    private fun save(c: Context) {
        Prefs.sp(c).edit()
            .putBoolean("onboarded", onboarded)
            .putBoolean("signedIn", signedIn)
            .putString("name", name)
            .putString("email", email)
            .apply()
    }
}

/** يجهّز صورة من المعرض للإرسال: يصغّرها ويحوّلها JPEG بصيغة base64. */
object ImageUtil {
    fun encode(c: Context, uri: Uri): String? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            val longSide = maxOf(opts.outWidth, opts.outHeight)
            while (longSide / sample > 1600) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
            bmp.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Throwable) {
            null
        }
    }
}

/** قراءة الردود بصوت عالٍ (اختياري من الإعدادات). */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var tag = ""

    fun init(c: Context) {
        if (tts != null) return
        tts = TextToSpeech(c.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) apply()
        }
    }

    private fun apply() {
        val engine = tts ?: return
        tag = GuideState.language
        ready = listOf(Locale.forLanguageTag(tag), Locale(tag.substringBefore('-'))).any { loc ->
            val r = try {
                engine.setLanguage(loc)
            } catch (_: Throwable) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String) {
        if (tag != GuideState.language) apply()
        if (!ready) return
        try {
            SpeechChunker.split(Markdown.plain(text)).forEachIndexed { i, c ->
                tts?.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "chat-$i")
            }
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
    }
}

/** المحادثات المحفوظة على الهاتف ومنطق الدردشة. */
object ChatStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var app: Context? = null
    private var seq = 0L

    var convs by mutableStateOf<List<Conv>>(emptyList())
    var currentId by mutableStateOf<Long?>(null)
    var thinking by mutableStateOf(false)
    var streaming by mutableStateOf("")
    var pendingImage by mutableStateOf<String?>(null)
    private val main = Handler(Looper.getMainLooper())
    private var reqId = 0
    private var genCid: Long? = null

    val current: Conv? get() = convs.firstOrNull { it.id == currentId }

    private fun nid(): Long = System.currentTimeMillis() * 1000 + (seq++ % 1000)

    fun load(c: Context) {
        app = c.applicationContext
        try {
            val f = File(c.filesDir, "chats.json")
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            val out = mutableListOf<Conv>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ms = o.getJSONArray("msgs")
                val list = mutableListOf<Msg>()
                for (k in 0 until ms.length()) {
                    val m = ms.getJSONObject(k)
                    val acts = mutableListOf<Action>()
                    val aa = m.optJSONArray("actions")
                    if (aa != null) {
                        for (q in 0 until aa.length()) {
                            val a = aa.getJSONObject(q)
                            acts += Action(a.optString("type"), a.optString("label"), a.optString("url"), a.optString("goal"))
                        }
                    }
                    list += Msg(m.getLong("id"), m.getString("role"), m.getString("text"), acts, m.optString("engine"), m.optBoolean("img"))
                }
                out += Conv(o.getLong("id"), o.getString("title"), list, o.optLong("updated"))
            }
            convs = out.sortedByDescending { it.updated }
        } catch (_: Throwable) {
        }
    }

    private fun save() {
        val c = app ?: return
        val arr = JSONArray()
        convs.forEach { cv ->
            val ms = JSONArray()
            cv.msgs.forEach { m ->
                val aa = JSONArray()
                m.actions.forEach { a ->
                    aa.put(JSONObject().put("type", a.type).put("label", a.label).put("url", a.url).put("goal", a.goal))
                }
                ms.put(
                    JSONObject().put("id", m.id).put("role", m.role).put("text", m.text).put("actions", aa)
                        .put("engine", m.engine).put("img", m.hasImage)
                )
            }
            arr.put(JSONObject().put("id", cv.id).put("title", cv.title).put("updated", cv.updated).put("msgs", ms))
        }
        val text = arr.toString()
        scope.launch(Dispatchers.IO) {
            try {
                File(c.filesDir, "chats.json").writeText(text)
            } catch (_: Throwable) {
            }
        }
    }

    fun newChat() {
        currentId = null
    }

    fun open(id: Long) {
        currentId = id
    }

    fun rename(id: Long, title: String) {
        val t = title.trim().take(60)
        if (t.isEmpty()) return
        convs = convs.map { if (it.id == id) it.copy(title = t) else it }
        save()
    }

    /** بحث في عناوين المحادثات ونصوص رسائلها. */
    fun search(query: String): List<Conv> {
        val q = query.trim()
        if (q.isEmpty()) return convs
        return convs.filter { c -> c.title.contains(q, ignoreCase = true) || c.msgs.any { it.text.contains(q, ignoreCase = true) } }
    }

    /** يعيد توليد آخر رد: يحذفه ويطلب غيره من الرسالة السابقة. */
    fun regenerate() {
        val c = current ?: return
        if (thinking) return
        val last = c.msgs.lastOrNull() ?: return
        if (last.role != "assistant" || c.msgs.none { it.role == "user" }) return
        convs = convs.map { if (it.id == c.id) it.copy(msgs = it.msgs.dropLast(1)) else it }
        save()
        generate(c.id)
    }

    fun delete(id: Long) {
        convs = convs.filter { it.id != id }
        if (currentId == id) currentId = null
        save()
    }

    fun clearAll() {
        app?.let { Prefs.clearGuideData(it) }
        reqId++
        convs = emptyList()
        currentId = null
        thinking = false
        streaming = ""
        save()
    }

    private fun append(cid: Long, m: Msg) {
        convs = convs
            .map { if (it.id == cid) it.copy(msgs = it.msgs + m, updated = System.currentTimeMillis()) else it }
            .sortedByDescending { it.updated }
        save()
    }

    private fun ensureCurrent(title: String): Long {
        val cur = current
        if (cur != null) return cur.id
        val c = Conv(nid(), title.take(40), emptyList(), System.currentTimeMillis())
        convs = listOf(c) + convs
        currentId = c.id
        return c.id
    }

    private fun statusLine(): String {
        val mic = if (GuideState.micGranted) "microphone: granted" else "microphone: not granted"
        val acc = if (GuideState.serviceConnected) "accessibility service: enabled" else "accessibility service: not enabled"
        val who = if (Account.name.isNotBlank()) "user name: ${Account.name}" else "guest user"
        return "$mic, $acc, $who, app language: ${L.current.english}"
    }

    /** يرتّب البطاقات: الأذونات الناقصة أولاً ثم فتح الرابط ثم بدء الإرشاد، دون تكرار. */
    private fun normalize(actions: List<Action>): List<Action> {
        val wantsGuide = actions.any { it.type == "start_guide" }
        val out = mutableListOf<Action>()
        if (!GuideState.micGranted && (wantsGuide || actions.any { it.type == "ask_mic" })) {
            out += Action("ask_mic")
        }
        if (!GuideState.serviceConnected && (wantsGuide || actions.any { it.type == "ask_accessibility" })) {
            out += Action("ask_accessibility")
        }
        actions.firstOrNull { it.type == "open_url" && it.url.startsWith("https://") }?.let { out += it }
        actions.firstOrNull { it.type == "start_guide" }?.let {
            out += it.copy(goal = it.goal.ifBlank { L.s(R.string.default_goal) })
        }
        return out
    }

    fun send(text: String) {
        val img = pendingImage
        val t = text.trim().ifEmpty { if (img != null) L.s(R.string.img_default_q) else "" }
        if (t.isEmpty() || thinking) return
        pendingImage = null
        Speaker.stop()
        val cid = ensureCurrent(t)
        append(cid, Msg(nid(), "user", t, hasImage = img != null))
        generate(cid, img)
    }

    /** بعد فشل الاتصال: يعيد طلب آخر رسالة دون تكرارها في المحادثة. */
    fun retry() {
        val c = current ?: return
        if (thinking) return
        val last = c.msgs.lastOrNull() ?: return
        if (last.role != "assistant" || last.actions.none { it.type == "retry" }) return
        convs = convs.map { if (it.id == c.id) it.copy(msgs = it.msgs.dropLast(1)) else it }
        generate(c.id)
    }

    /** زر الإيقاف: يحتفظ بما وصل من الرد ويتجاهل ما بعده. */
    fun stop() {
        if (!thinking) return
        reqId++
        val cid = genCid
        val partial = streaming
        thinking = false
        streaming = ""
        if (cid != null) append(cid, Msg(nid(), "assistant", partial.ifBlank { L.s(R.string.reply_stopped) }))
    }

    private fun generate(cid: Long, image: String? = null) {
        val my = ++reqId
        genCid = cid
        thinking = true
        streaming = ""
        val raw = convs.first { it.id == cid }.msgs.takeLast(14).map { Turn(it.role, it.text) }
        val hist = if (image != null && raw.isNotEmpty()) raw.dropLast(1) + raw.last().copy(image = image) else raw
        val status = statusLine()
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                AiClient.chatStream(
                    hist, status,
                    { t -> main.post { if (my == reqId) streaming = t } },
                    { my != reqId }
                )
            }
            if (my != reqId) return@launch
            thinking = false
            streaming = ""
            res.fold(
                { r ->
                    GuideState.engine = r.engine
                    append(cid, Msg(nid(), "assistant", r.text, normalize(r.actions), r.engine))
                    if (GuideState.speakReplies) Speaker.speak(r.text)
                },
                {
                    append(
                        cid,
                        Msg(nid(), "assistant", L.s(R.string.ai_unreachable), listOf(Action("retry")))
                    )
                }
            )
        }
    }

    /** أول رسالة بعد الدخول: ترحيب وبطاقتا الإذنين. */
    fun startWelcome() {
        if (convs.isNotEmpty()) return
        val first = Account.name.trim().substringBefore(' ')
        val hello = if (first.isNotBlank()) L.f(R.string.welcome_hello_name, first) else L.s(R.string.welcome_hello)
        val body = hello + "\n" + L.s(R.string.welcome_body)
        val c = Conv(
            nid(), L.s(R.string.title_start),
            listOf(Msg(nid(), "assistant", body, listOf(Action("ask_mic"), Action("ask_accessibility")))),
            System.currentTimeMillis()
        )
        convs = listOf(c)
        currentId = c.id
        save()
    }

    /** من شريط «الإعداد غير مكتمل»: يضيف رسالة فيها بطاقات الإذنين الناقصين. */
    fun addSetupCards() {
        val cid = ensureCurrent(L.s(R.string.title_setup))
        val acts = mutableListOf<Action>()
        if (!GuideState.micGranted) acts += Action("ask_mic")
        if (!GuideState.serviceConnected) acts += Action("ask_accessibility")
        if (acts.isEmpty()) return
        append(cid, Msg(nid(), "assistant", L.s(R.string.setup_cards_msg), acts))
    }
}
