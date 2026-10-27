package com.khotwa.ai

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream

/**
 * قلب التطبيق: زر عائم + مايك مباشر + عين على الشاشة (تعرف أين أنت وأين كل زر) + ذكاء اصطناعي + نطق
 * + دائرة صغيرة على الزر المطلوب. لا يضغط نيابةً عن المستخدم ولا يكتب عنه، ولا يقرأ محتوى الحقول ولا كلمات المرور.
 */
class KhotwaAccessibilityService : AccessibilityService(), VoiceEngine.Listener {

    private sealed class Snap {
        class Ok(val m: ScreenModel) : Snap()
        class Fail(val message: String) : Snap()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val reader by lazy { ScreenReader(this) }
    private var windowManager: WindowManager? = null
    private var overlay: GuideOverlayView? = null
    private var button: FloatingButtonView? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var btnX = 0f
    private var btnY = 0f
    private var voice: VoiceEngine? = null

    // حالة الجلسة الصوتية
    private var sessionActive = false
    private var endAfterSpeak = false
    private var idleListens = 0
    private var errorRetries = 0
    private var autoSteps = 0
    private var requestId = 0
    private var langOverride: String? = null
    private val history = mutableListOf<Turn>()
    private var foregroundOn = false

    // الدائرة الحالية
    private var pointLabel: String? = null
    private var pointRect: Rect? = null
    private var pointAuto = false
    private var refreshScheduled = false

    // وعي الصفحة: أين أنت الآن وما الذي تغيّر
    private var askedModel: ScreenModel? = null
    private var pendingClick: String? = null
    private var lastPageKey = ""
    private var lastChangeAt = 0L
    private var awarenessScheduled = false
    private var settleStartAt = 0L
    private var settleQuestion = ""
    private var scrollHintDir = 0

    private val speakDoneRunnable = Runnable { onSpeakDone() }
    private val hideMsgRunnable = Runnable { overlay?.hideMessage() }
    private val hidePointRunnable = Runnable { clearPoint() }
    private val hideChipRunnable = Runnable { overlay?.showChip(null) }
    private val hideScrollRunnable = Runnable { clearScrollHint() }
    private val settleRunnable = Runnable { settleTick() }
    private val awarenessRunnable = Runnable {
        awarenessScheduled = false
        if (sessionActive) checkPage(false)
    }
    private val sessionTimeoutRunnable = Runnable {
        if (sessionActive) endSession(L.s(R.string.svc_battery_stop))
    }
    private val refreshRunnable = Runnable {
        refreshScheduled = false
        refreshPoint()
    }

    // ───────────────────────── دورة حياة الخدمة ─────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        removeViews()
        instance = this
        Aliases.load(Prefs.sp(this).getString("aliases", "") ?: "")
        loadHistory()
        Prefs.load(this)
        OnDeviceAi.refresh()
        GuideState.serviceConnected = true
        GuideState.micGranted = hasMic()

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        // 1) طبقة الدائرة والفقاعة (لا تعترض اللمس)
        val view = GuideOverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        wm.addView(view, params)
        overlay = view

        // 2) الزر الدائري العائم (قابل للسحب) فوق كل شيء
        val dm = resources.displayMetrics
        val size = (68 * dm.density).toInt()
        val saved = Prefs.buttonPos(this)
        btnX = (saved?.first ?: (dm.widthPixels - size - (4 * dm.density).toInt())).toFloat()
        btnY = (saved?.second ?: (dm.heightPixels * 0.6f).toInt()).toFloat()
        val btn = FloatingButtonView(this)
        val bp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = btnX.toInt()
            y = btnY.toInt()
        }
        btn.onTap = { onButtonTap() }
        btn.onDrag = { dx, dy -> moveButton(dx, dy) }
        btn.onDragEnd = { Prefs.saveButtonPos(this, bp.x, bp.y) }
        wm.addView(btn, bp)
        button = btn
        buttonParams = bp
        btn.visibility = if (GuideState.appForeground) View.GONE else View.VISIBLE

        // 3) المايك والنطق
        voice?.shutdown()
        voice = VoiceEngine(this, handler).also { it.listener = this }
        setPhase(Phase.IDLE)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun removeViews() {
        val wm = windowManager
        overlay?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        button?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        overlay = null
        button = null
        buttonParams = null
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        sessionActive = false
        stopForegroundBestEffort()
        voice?.shutdown()
        voice = null
        removeViews()
        if (instance === this) instance = null
        GuideState.serviceConnected = false
        GuideState.phase = Phase.IDLE
        GuideState.busy = false
    }

    // ───────────────────────── الزر والجلسة ─────────────────────────

    private fun setPhase(p: Phase) {
        GuideState.phase = p
        button?.phase = p
    }

    private fun moveButton(dx: Float, dy: Float) {
        val bp = buttonParams ?: return
        val v = button ?: return
        val dm = resources.displayMetrics
        val size = (68 * dm.density)
        btnX = (btnX + dx).coerceIn(0f, (dm.widthPixels - size).coerceAtLeast(0f))
        btnY = (btnY + dy).coerceIn(0f, (dm.heightPixels - size).coerceAtLeast(0f))
        bp.x = btnX.toInt()
        bp.y = btnY.toInt()
        try {
            windowManager?.updateViewLayout(v, bp)
        } catch (_: Throwable) {
        }
    }

    private fun onButtonTap() {
        when {
            !sessionActive -> startSession()
            GuideState.phase == Phase.SPEAKING -> {
                // مقاطعة: أوقف الكلام واستمع فوراً
                handler.removeCallbacks(speakDoneRunnable)
                voice?.stopSpeaking()
                endAfterSpeak = false
                listen()
            }
            else -> endSession(null)
        }
    }

    private fun hasMic(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Throwable) {
        }
    }

    private fun startSession(initialGoal: String? = null) {
        val v = voice ?: return
        if (!GuideState.consent) {
            announce(L.s(R.string.svc_need_login))
            openApp()
            return
        }
        if (!hasMic()) {
            GuideState.micGranted = false
            announce(L.s(R.string.svc_need_mic))
            openApp()
            return
        }
        GuideState.micGranted = true
        if (!v.isRecognitionAvailable()) {
            announce(L.s(R.string.svc_no_speech))
            return
        }
        sessionActive = true
        endAfterSpeak = false
        idleListens = 0
        errorRetries = 0
        autoSteps = 0
        langOverride = null
        history.clear()
        saveHistory()
        askedModel = null
        pendingClick = null
        lastPageKey = ""
        clearPoint()
        clearScrollHint()
        startForegroundBestEffort()
        touchSession()
        // أول ما تبدأ الجلسة: أخبرها أين أنت فوراً
        checkPage(true)
        if (initialGoal != null) {
            overlay?.showMessage(L.s(R.string.svc_thinking))
            askAi("My goal: $initialGoal. Start guiding me step by step.")
        } else {
            overlay?.showMessage(L.s(R.string.svc_listening_hint))
            listen()
        }
    }

    /** يُستدعى من الدردشة: يبدأ الإرشاد بهدف جاهز بعد أن يخرج المستخدم إلى التطبيق المطلوب. */
    fun startGuide(goal: String) {
        handler.postDelayed({
            if (sessionActive) endSession(null)
            startSession(goal)
        }, 1400)
    }

    /** يُخفي الزر العائم أثناء وجود المستخدم داخل تطبيق خُطوة نفسه. */
    fun setButtonHidden(hidden: Boolean) {
        button?.visibility = if (hidden) View.GONE else View.VISIBLE
    }

    private fun touchSession() {
        handler.removeCallbacks(sessionTimeoutRunnable)
        handler.postDelayed(sessionTimeoutRunnable, 8 * 60 * 1000L)
    }

    private fun endSession(message: String?, keepMessage: Boolean = false) {
        sessionActive = false
        endAfterSpeak = false
        requestId++
        pendingClick = null
        awarenessScheduled = false
        handler.removeCallbacks(settleRunnable)
        handler.removeCallbacks(awarenessRunnable)
        handler.removeCallbacks(speakDoneRunnable)
        handler.removeCallbacks(sessionTimeoutRunnable)
        handler.removeCallbacks(hideChipRunnable)
        overlay?.showChip(null)
        clearScrollHint()
        voice?.stopListening()
        voice?.stopSpeaking()
        stopForegroundBestEffort()
        GuideState.busy = false
        overlay?.setHeard(null)
        if (message != null) {
            announce(message)
        } else {
            setPhase(Phase.IDLE)
            if (keepMessage) {
                handler.removeCallbacks(hideMsgRunnable)
                handler.postDelayed(hideMsgRunnable, 15000)
            } else {
                clearPoint()
                overlay?.hideMessage()
            }
        }
    }

    private fun listen() {
        if (!sessionActive) return
        setPhase(Phase.LISTENING)
        try {
            voice?.startListening(langOverride ?: GuideState.language)
        } catch (_: Throwable) {
            endSession(L.s(R.string.svc_mic_failed))
        }
    }

    // ───────────────────────── استقبال الصوت ─────────────────────────

    override fun onPartial(text: String) {
        if (sessionActive) overlay?.setHeard(text)
    }

    override fun onFinal(text: String) {
        if (!sessionActive) return
        val clean = text.trim()
        if (clean.isEmpty()) {
            onListenError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }
        idleListens = 0
        errorRetries = 0
        touchSession()
        overlay?.setHeard(clean)
        if (isStopCommand(clean)) {
            endSession(L.s(R.string.svc_bye))
            return
        }
        autoSteps = 0
        if (Arabic.isWhereAmI(clean)) {
            // «أين أنا؟» تُجاب فوراً من الشاشة دون انتظار الشبكة
            answerWhere()
            return
        }
        askAi(clean)
    }

    override fun onListenError(code: Int) {
        if (!sessionActive) return
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                idleListens++
                if (idleListens >= 3) {
                    endSession(L.s(R.string.svc_stopped_listening))
                } else {
                    handler.postDelayed({ listen() }, 300)
                }
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                GuideState.micGranted = false
                endSession(L.s(R.string.svc_need_mic))
                openApp()
            }
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                endSession(L.s(R.string.svc_weak_net))
            12, 13 -> {
                // اللهجة غير مدعومة في هذا الهاتف: جرّب اللغة العامة مرة واحدة
                if (langOverride == null) {
                    langOverride = L.current.voices.first().first.let { if (it == GuideState.language) L.code else it }
                    handler.postDelayed({ listen() }, 300)
                } else {
                    endSession(L.s(R.string.svc_lang_missing))
                }
            }
            else -> {
                errorRetries++
                if (errorRetries > 2) {
                    endSession(L.f(R.string.svc_listen_error, code))
                } else {
                    handler.postDelayed({ listen() }, 700)
                }
            }
        }
    }

    override fun onSpeakDone() {
        handler.removeCallbacks(speakDoneRunnable)
        if (!sessionActive) {
            GuideState.busy = false
            setPhase(Phase.IDLE)
            return
        }
        if (endAfterSpeak) {
            endSession(null, keepMessage = true)
        } else {
            listen()
        }
    }

    private fun isStopCommand(text: String): Boolean {
        val words = text.trim().lowercase().split(Regex("\\s+")).map { it.trim('.', '!', '،', ',', '?', '؟') }.filter { it.isNotEmpty() }
        return words.size <= 3 && words.any { it in STOP_WORDS }
    }

    // ───────────────────────── الذكاء الاصطناعي ─────────────────────────

    /** سؤال كتابي من داخل التطبيق: يقرأ الشاشة بعد مهلة ليفتح المستخدم التطبيق الآخر. */
    fun askTextDelayed(question: String, delayMs: Long) {
        if (!GuideState.consent) {
            GuideState.lastResult = L.s(R.string.svc_need_login)
            return
        }
        GuideState.busy = true
        GuideState.lastResult = L.f(R.string.svc_reader_countdown, delayMs / 1000)
        handler.postDelayed({ askAi(question) }, delayMs)
    }

    private fun askAi(question: String, auto: Boolean = false) {
        // تعليم الأسماء: «هذا اسمه الدفع» بعد أن أشار التطبيق إلى زر.
        if (!auto) {
            val name = Aliases.teachName(question)
            val label = pointLabel
            if (name != null && label != null && Aliases.add(name, label)) {
                Prefs.sp(this).edit().putString("aliases", Aliases.serialize()).apply()
                deliver(L.f(R.string.alias_saved, name, label), false)
                return
            }
        }
        val myId = ++requestId
        val click = pendingClick
        pendingClick = null
        voice?.stopListening()
        voice?.stopSpeaking()
        handler.removeCallbacks(settleRunnable)
        GuideState.busy = true
        setPhase(Phase.THINKING)
        overlay?.showMessage(L.s(R.string.svc_thinking))
        clearScrollHint()
        val vision = GuideState.vision && AiClient.hasVision()
        when (val snap = snapshotScreen(vision)) {
            is Snap.Fail -> deliver(snap.message, false)
            is Snap.Ok -> {
                val m = snap.m
                lastPageKey = m.pageKey
                GuideState.where = m.where()
                showWhere(m.where())
                val q = composeQuestion(question, click, m)
                if (vision) {
                    captureMarked(snap) { b64 -> sendToAi(myId, q, snap, b64, auto) }
                } else {
                    sendToAi(myId, q, snap, null, auto)
                }
            }
        }
    }

    /** إن كان المستخدم قد ضغط الزر المشار إليه، نخبر الذكاء ماذا تغيّر على الشاشة بعد ضغطته. */
    private fun composeQuestion(question: String, click: String?, m: ScreenModel): String {
        if (click == null) return question
        val prev = askedModel
        val t = Transition.describe(
            prev?.pageKey, prev?.shortWhere().orEmpty(), prev?.fingerprint ?: 0,
            m.pageKey, m.shortWhere(), m.fingerprint
        )
        return "I tapped \"$click\". $t Did we reach the right place, and what is the next step?".replace("  ", " ")
    }

    private fun sendToAi(myId: Int, question: String, snap: Snap.Ok, image: String?, auto: Boolean) {
        if (myId != requestId) return
        val hist = history.toList()
        Thread {
            val res = AiClient.ask(question, snap.m, hist, image, auto)
            handler.post { onAiResult(myId, question, snap, res) }
        }.start()
    }

    /** «أين أنا؟»: جواب فوري من قراءة الشاشة دون شبكة. */
    private fun answerWhere() {
        val root = rootInActiveWindow
        val info = if (root == null) null else reader.peek(root, packageName)
        if (info != null) {
            lastPageKey = info.key
            GuideState.where = info.where()
            showWhere(info.where())
        }
        deliver(if (info == null) L.s(R.string.svc_cant_read) else L.f(R.string.svc_where, info.where()), false)
    }

    // ───────────────────────── وعي الصفحة ─────────────────────────

    private fun showWhere(text: String) {
        overlay?.showChip(L.f(R.string.svc_chip, text))
        handler.removeCallbacks(hideChipRunnable)
        handler.postDelayed(hideChipRunnable, 3500)
    }

    /** يتأكد هل انتقل المستخدم لتطبيق أو موقع أو صفحة أخرى، ويُظهر شارة «أراك الآن في…» عند الانتقال. */
    private fun checkPage(force: Boolean) {
        val root = rootInActiveWindow ?: return
        val info = reader.peek(root, packageName) ?: return
        if (!force && info.key == lastPageKey) return
        lastPageKey = info.key
        GuideState.where = info.where()
        showWhere(info.where())
    }

    private fun scheduleAwareness() {
        if (awarenessScheduled) return
        awarenessScheduled = true
        handler.postDelayed(awarenessRunnable, 900)
    }

    /** ننتظر حتى تستقر الشاشة (لا تغيّر لنصف ثانية) ثم نسأل الذكاء عن الخطوة التالية. */
    private fun beginSettle(question: String, firstDelayMs: Long) {
        settleQuestion = question
        settleStartAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, firstDelayMs)
    }

    private fun settleTick() {
        if (!sessionActive) return
        val now = SystemClock.uptimeMillis()
        val quiet = now - lastChangeAt >= SETTLE_QUIET_MS
        val timedOut = now - settleStartAt >= SETTLE_MAX_MS
        if (quiet || timedOut) {
            askAi(settleQuestion, true)
        } else {
            handler.postDelayed(settleRunnable, 250)
        }
    }

    private fun showScrollHint(dir: String) {
        scrollHintDir = if (dir == "up") -1 else 1
        overlay?.showScrollHint(scrollHintDir)
        handler.removeCallbacks(hideScrollRunnable)
        handler.postDelayed(hideScrollRunnable, 15000)
    }

    private fun clearScrollHint() {
        scrollHintDir = 0
        handler.removeCallbacks(hideScrollRunnable)
        overlay?.hideScrollHint()
    }

    /** مرّر المستخدم بعد أن طلبنا منه التمرير: ننتظر استقرار الشاشة ثم نبحث عن الزر. */
    private fun onUserScrolled() {
        if (!sessionActive || scrollHintDir == 0) return
        if (autoSteps >= MAX_AUTO_STEPS) return
        autoSteps++
        clearScrollHint()
        voice?.stopListening()
        voice?.stopSpeaking()
        handler.removeCallbacks(speakDoneRunnable)
        touchSession()
        setPhase(Phase.THINKING)
        beginSettle("I scrolled the screen. Can you see what we are looking for now, and what is the next step?", 450)
    }

    // ───────────────────────── عين خُطوة: لقطة الشاشة ─────────────────────────

    /** يلتقط الشاشة ويرسم أرقام العناصر عليها، ثم يعيدها JPEG بصيغة base64 (أو null عند الفشل). */
    private fun captureMarked(snap: Snap.Ok, cb: (String?) -> Unit) {
        val ov = overlay
        val bt = button
        // نخفي دائرتنا والزر والفقاعة لحظة الالتقاط كي لا تظهر في الصورة
        ov?.visibility = View.INVISIBLE
        bt?.visibility = View.INVISIBLE
        handler.postDelayed({
            var done = false
            fun finish(b64: String?) {
                if (done) return
                done = true
                ov?.visibility = View.VISIBLE
                bt?.visibility = if (GuideState.appForeground) View.GONE else View.VISIBLE
                cb(b64)
            }
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            var b64: String? = null
                            try {
                                val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                val soft = hw?.copy(Bitmap.Config.ARGB_8888, true)
                                result.hardwareBuffer.close()
                                if (soft != null) b64 = encodeMarked(soft, snap.m)
                            } catch (_: Throwable) {
                            }
                            finish(b64)
                        }

                        override fun onFailure(errorCode: Int) {
                            finish(null)
                        }
                    }
                )
            } catch (_: Throwable) {
                finish(null)
            }
            // احتياط: إن لم يرد النظام خلال 3 ثوانٍ نكمل بالنص فقط
            handler.postDelayed({ finish(null) }, 3000)
        }, 160)
    }

    private fun encodeMarked(soft: Bitmap, m: ScreenModel): String? {
        val maxSide = maxOf(soft.width, soft.height).toFloat()
        val s = minOf(1f, 1400f / maxSide)
        val scaled = if (s < 1f) Bitmap.createScaledBitmap(soft, (soft.width * s).toInt(), (soft.height * s).toInt(), true) else soft
        if (scaled !== soft) soft.recycle()
        val canvas = Canvas(scaled)
        val dm = resources.displayMetrics
        val sx = scaled.width.toFloat() / dm.widthPixels
        val sy = scaled.height.toFloat() / dm.heightPixels
        val tagR = 13f
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0xAA14B8A6.toInt()
        }
        val tagBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE11D48.toInt() }
        val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 15f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        m.rects.forEachIndexed { i, r ->
            if (i >= 90) return@forEachIndexed
            val l = r.left * sx
            val t = r.top * sy
            canvas.drawRect(l, t, r.right * sx, r.bottom * sy, outline)
            val cx = (l + tagR).coerceIn(tagR, scaled.width - tagR)
            val cy = (t + tagR).coerceIn(tagR, scaled.height - tagR)
            canvas.drawCircle(cx, cy, tagR, tagBg)
            canvas.drawText(i.toString(), cx, cy + 5.2f, tagText)
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
        scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun onAiResult(id: Int, question: String, snap: Snap.Ok, res: Result<AiReply>) {
        if (id != requestId) return
        res.fold(
            { r ->
                history += Turn("user", question)
                history += Turn("assistant", AiClient.historyText(r))
                while (history.size > 8) {
                    history.removeAt(0)
                    history.removeAt(0)
                }
                saveHistory()
                val m = snap.m
                askedModel = m
                GuideState.engine = r.engine
                val idx = r.targetId
                val el = idx?.let { m.els.getOrNull(it) }
                val oldRect = idx?.let { m.rects.getOrNull(it) }
                if (idx != null && el != null && oldRect != null && r.confidence >= 0.65) {
                    val fresh = findByLabel(el.label, oldRect)
                    if (fresh != null) point(el.label, fresh, el.editable) else clearPoint()
                } else {
                    clearPoint()
                }
                val scroll = r.scroll
                if (pointLabel == null && scroll != null) showScrollHint(scroll)
                deliver(r.say, r.done, GuideProtocol.stepLabel(r.step, r.total))
            },
            {
                deliver(L.s(R.string.ai_unreachable), false)
            }
        )
    }

    /** يعرض الرد ويقرؤه بصوت عالٍ، ثم يعود للاستماع إن كانت الجلسة مفتوحة. */
    private fun deliver(text: String, done: Boolean, step: String? = null) {
        GuideState.busy = false
        GuideState.lastResult = text
        endAfterSpeak = done && sessionActive
        overlay?.showMessage(text, step)
        handler.removeCallbacks(hideMsgRunnable)
        if (!sessionActive) handler.postDelayed(hideMsgRunnable, 15000)
        setPhase(Phase.SPEAKING)
        speakWithFallback(text)
    }

    /** رسالة قصيرة خارج المحادثة (تنبيه أو وداع). */
    private fun announce(text: String) {
        GuideState.lastResult = text
        overlay?.showMessage(text)
        handler.removeCallbacks(hideMsgRunnable)
        handler.postDelayed(hideMsgRunnable, 15000)
        setPhase(Phase.SPEAKING)
        speakWithFallback(text)
    }

    private fun speakWithFallback(text: String) {
        handler.removeCallbacks(speakDoneRunnable)
        val spoken = Markdown.plain(text)
        val ok = voice?.speak(spoken) == true
        // مهلة احتياطية تتناسب مع طول الشرح، فلا يُقطع الشرح الطويل قبل أن يكتمل.
        val delay = if (ok) SpeechChunker.estimateMs(spoken) + 8000L else 1800L + spoken.length * 55L
        handler.postDelayed(speakDoneRunnable, delay)
    }

    // ───────────────────────── الدائرة على الزر ─────────────────────────

    private fun point(label: String, rect: Rect, editable: Boolean) {
        if (pointLabel != label) buzz()
        pointLabel = label
        pointRect = rect
        pointAuto = !editable
        overlay?.showTarget(rect)
        handler.removeCallbacks(hidePointRunnable)
        handler.postDelayed(hidePointRunnable, 40000)
    }

    /** يحفظ محادثة الإرشاد على الجهاز، فإن خرج المستخدم وعاد أكمل من حيث توقف. */
    private fun saveHistory() {
        try {
            val a = org.json.JSONArray()
            history.forEach { a.put(org.json.JSONObject().put("r", it.role).put("t", it.text)) }
            Prefs.sp(this).edit().putString("guide_hist", a.toString()).apply()
        } catch (_: Throwable) {
        }
    }

    private fun loadHistory() {
        if (history.isNotEmpty()) return
        try {
            val a = org.json.JSONArray(Prefs.sp(this).getString("guide_hist", "[]") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                history += Turn(o.getString("r"), o.getString("t"))
            }
        } catch (_: Throwable) {
            history.clear()
        }
    }

    /** اهتزاز خفيف عند تحديد زر جديد، ليشعر المستخدم بالوصول دون النظر إلى الشاشة. */
    private fun buzz() {
        try {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31) {
                getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(android.os.Vibrator::class.java)
            }
            v?.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Throwable) {
        }
    }

    private fun clearPoint() {
        pointLabel = null
        pointRect = null
        pointAuto = false
        handler.removeCallbacks(hidePointRunnable)
        overlay?.hideTarget()
    }

    private fun scheduleRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        handler.postDelayed(refreshRunnable, 350)
    }

    private fun refreshPoint() {
        val label = pointLabel ?: return
        val r = findByLabel(label, pointRect)
        if (r != null) {
            pointRect = r
            overlay?.showTarget(r)
        } else {
            overlay?.hideTarget()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName?.toString() == packageName) return
        val type = event.eventType
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            lastChangeAt = SystemClock.uptimeMillis()
        }
        if (sessionActive) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                scheduleAwareness()
            }
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED && scrollHintDir != 0) onUserScrolled()
        }
        if (pointLabel == null) return
        when (type) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> onClickEvent(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> clearPoint()
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // الدائرة العالقة في مكان خاطئ أسوأ من عدمها: نخفيها ثم نعيد الحساب.
                overlay?.hideTarget()
                scheduleRefresh()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleRefresh()
        }
    }

    private var lastWrongAt = 0L

    private fun onClickEvent(event: AccessibilityEvent) {
        if (!sessionActive) return
        val tr = pointRect ?: return
        val src = event.source ?: return
        val r = Rect()
        src.getBoundsInScreen(r)
        if (r.isEmpty) return
        val slop = (16 * resources.displayMetrics.density).toInt()
        val big = Rect(tr.left - slop, tr.top - slop, tr.right + slop, tr.bottom + slop)
        when {
            big.contains(r.centerX(), r.centerY()) -> if (pointAuto) onTargetClicked()
            r.contains(tr.centerX(), tr.centerY()) -> {} // حاوية كبيرة تضم الهدف: لا نعتبرها خطأ
            else -> onWrongClick(reader.labelOf(src), tr)
        }
    }

    /** ضغط خاطئ: تصحيح صوتي فوري بلا انتظار الذكاء الاصطناعي، ثم إعادة توجيه إن تغيّرت الشاشة. */
    private fun onWrongClick(clicked: String, targetRect: Rect) {
        val label = pointLabel ?: return
        val now = System.currentTimeMillis()
        if (now - lastWrongAt < 5000) return
        lastWrongAt = now
        val dm = resources.displayMetrics
        val place = Regions.placeOf(targetRect.centerX(), targetRect.centerY(), dm.widthPixels, dm.heightPixels)
        val msg = if (place.isEmpty()) L.f(R.string.svc_not_here, label.take(30))
        else L.f(R.string.svc_not_here_at, label.take(30), L.f(R.string.off_at_place, Regions.placeName(place)))
        deliver(msg, false)
        handler.postDelayed({
            if (!sessionActive) return@postDelayed
            if (findByLabel(label, targetRect) == null) {
                clearPoint()
                val what = clicked.ifBlank { "another element" }
                askAi("I tapped \"$what\" by mistake instead of \"$label\". Where am I now and how do I continue?", true)
            }
        }, 1800)
    }

    /** ضغط المستخدم على العنصر المشار إليه: ننتظر استقرار الشاشة ثم نسأل عن الخطوة التالية. */
    private fun onTargetClicked() {
        val label = pointLabel ?: return
        if (autoSteps >= MAX_AUTO_STEPS) return
        autoSteps++
        pendingClick = label
        clearPoint()
        voice?.stopListening()
        voice?.stopSpeaking()
        handler.removeCallbacks(speakDoneRunnable)
        touchSession()
        setPhase(Phase.THINKING)
        beginSettle("", 600)
    }

    // ───────────────────────── قراءة الشاشة ─────────────────────────

    /** يقرأ الشاشة بعد مهلة ويعرض ما تراه الخدمة (لقسم الشفافية في التطبيق). */
    fun readScreenDelayed(delayMs: Long) {
        GuideState.screenLines = emptyList()
        GuideState.lastResult = L.f(R.string.svc_reader_countdown, delayMs / 1000)
        handler.postDelayed({
            when (val snap = snapshotScreen()) {
                is Snap.Fail -> GuideState.lastResult = snap.message
                is Snap.Ok -> {
                    val m = snap.m
                    GuideState.where = m.where()
                    GuideState.screenLines = listOf("${L.s(R.string.svc_where_prefix)} ${m.where()}") + m.lines.take(40)
                    GuideState.lastResult = L.f(R.string.svc_reader_done, m.lines.size)
                }
            }
        }, delayMs)
    }

    private fun snapshotScreen(includeUnlabeled: Boolean = false): Snap {
        val root = rootInActiveWindow
            ?: return Snap.Fail(L.s(R.string.svc_cant_read))
        return when (val r = reader.read(root, includeUnlabeled, packageName)) {
            is ScreenReader.Read.Blocked -> Snap.Fail(
                when (r.reason) {
                    "own_app" -> L.s(R.string.svc_inside_app)
                    "protected_site" -> L.s(R.string.svc_protected_site)
                    else -> L.s(R.string.svc_protected)
                }
            )
            is ScreenReader.Read.Ok -> Snap.Ok(r.model)
        }
    }

    /** يعيد إيجاد العنصر باسمه في الشاشة الحالية (بعد تمرير أو تغيّر). */
    private fun findByLabel(label: String, near: Rect?): Rect? {
        val root = rootInActiveWindow ?: return null
        return reader.findByLabel(root, label, near, packageName)
    }

    // ───────────────────────── إشعار الاستماع ─────────────────────────

    /** إشعار «تستمع» يسمح للمايك بالعمل أثناء وجودك في تطبيق آخر. فشله لا يوقف شيئاً. */
    private fun startForegroundBestEffort() {
        if (foregroundOn) return
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, L.s(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_khotwa)
                .setContentTitle(L.s(R.string.svc_notif_title))
                .setContentText(L.s(R.string.svc_notif_text))
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            foregroundOn = true
        } catch (_: Throwable) {
        }
    }

    private fun stopForegroundBestEffort() {
        if (!foregroundOn) return
        foregroundOn = false
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
    }

    companion object {
        var instance: KhotwaAccessibilityService? = null

        private const val MAX_AUTO_STEPS = 15
        private const val SETTLE_QUIET_MS = 500L
        private const val SETTLE_MAX_MS = 5000L
        private const val CHANNEL_ID = "khotwa_listening"
        private const val NOTIF_ID = 4107

        private val STOP_WORDS = setOf(
            "توقف", "وقف", "كفاية", "كفايه", "خلاص", "شكرا", "شكراً", "بس",
            "اسكت", "ايقاف", "إيقاف", "stop", "thanks", "done", "arrête", "para", "pare", "dur", "berhenti",
            "रुको", "روکو"
        )
    }
}
