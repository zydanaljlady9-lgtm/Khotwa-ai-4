package com.khotwa.ai

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction

/**
 * عين خُطوة على شجرة الوصول: يحوّل ما على الشاشة إلى ScreenModel دقيق
 * (أين أنت: التطبيق والموقع والصفحة، وأسماء العناصر وأنواعها وإحداثياتها وأماكنها بالكلام، وحالة التمرير).
 * لا يقرأ محتوى الحقول ولا كلمات المرور، ويحجب تطبيقات ومواقع البنوك والمحافظ والمصادقة.
 */
class ScreenReader(private val ctx: Context) {

    sealed class Read {
        class Ok(val model: ScreenModel) : Read()
        /** سبب الحجب مفتاح يُترجم في الواجهة: protected_app, own_app, protected_site. */
        class Blocked(val reason: String) : Read()
    }

    private class Cand(
        val label: String,
        val kind: String,
        val rect: Rect,
        val editable: Boolean,
        val enabled: Boolean,
        val focused: Boolean,
        val interactive: Boolean,
        val suffix: String,
        val idHint: String
    )

    // ───────────── الواجهة العامة ─────────────

    /** قراءة كاملة للشاشة النشطة. includeUnnamed: يضمّ الأزرار بلا اسم (عند استعمال لقطة الشاشة). */
    fun read(root: AccessibilityNodeInfo, includeUnnamed: Boolean, ownPackage: String): Read {
        val pkg = root.packageName?.toString().orEmpty()
        if (Safety.isProtectedPackage(pkg)) {
            return Read.Blocked("protected_app")
        }
        if (pkg == ownPackage) {
            return Read.Blocked("own_app")
        }
        val pass = Pass(pkg, includeUnnamed, collect = true, maxVisible = MAX_VISIBLE, maxHidden = MAX_HIDDEN, budgetMs = READ_BUDGET_MS)
        pass.go(root, 0)
        if (Safety.isProtectedSite(pass.site)) {
            return Read.Blocked("protected_site")
        }
        return Read.Ok(pass.finish(root))
    }

    /** نظرة سريعة على «أين أنت» فقط (التطبيق والموقع والعنوان) دون قراءة كل العناصر. null إن كان محجوباً. */
    fun peek(root: AccessibilityNodeInfo, ownPackage: String): PageInfo? {
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == ownPackage || Safety.isProtectedPackage(pkg)) return null
        val pass = Pass(pkg, false, collect = false, maxVisible = PEEK_VISIBLE, maxHidden = 0, budgetMs = PEEK_BUDGET_MS)
        if (isBrowser(pkg)) pass.go(root, 0)
        if (Safety.isProtectedSite(pass.site)) return null
        return pass.page(root)
    }

    /**
     * يعيد إيجاد العنصر باسمه في الشاشة الحالية (بعد تمرير أو تغيّر). عند التكرار يختار الأقرب للموضع القديم.
     * المسار السريع: بحث النظام عن النص داخل التطبيق نفسه (استدعاء واحد)، ثم مرور محدود بالزمن إن لم نجده.
     */
    fun findByLabel(root: AccessibilityNodeInfo, label: String, near: Rect?, ownPackage: String): Rect? {
        val pkg = root.packageName?.toString().orEmpty()
        if (Safety.isProtectedPackage(pkg) || pkg == ownPackage) return null
        val dm = ctx.resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        val screenArea = sw.toLong() * sh
        val found = ArrayList<Rect>()

        fun consider(n: AccessibilityNodeInfo) {
            if (!n.isVisibleToUser) return
            val l = labelOf(n)
            val match = if (label == UNNAMED) (l.isEmpty() && n.isClickable) else l == label
            if (!match) return
            val own = Rect()
            n.getBoundsInScreen(own)
            if (own.isEmpty) return
            val r = Rect(clickTarget(n, own, screenArea).first)
            if (r.intersect(0, 0, sw, sh) && found.none { it == r }) found += r
        }

        if (label != UNNAMED && label.length >= 2) {
            try {
                val hits = root.findAccessibilityNodeInfosByText(label)
                if (hits != null) for (n in hits) consider(n)
            } catch (_: Throwable) {
            }
        }
        if (found.isEmpty()) {
            val deadline = SystemClock.uptimeMillis() + FIND_BUDGET_MS
            var visits = 0
            fun go(n: AccessibilityNodeInfo, depth: Int) {
                if (depth > MAX_DEPTH || visits >= MAX_VISIBLE || !n.isVisibleToUser) return
                if (SystemClock.uptimeMillis() > deadline) return
                visits++
                consider(n)
                for (i in 0 until n.childCount) {
                    if (SystemClock.uptimeMillis() > deadline) return
                    val c = n.getChild(i) ?: continue
                    go(c, depth + 1)
                }
            }
            go(root, 0)
        }
        return when {
            found.isEmpty() -> null
            found.size == 1 -> found[0]
            near != null -> found.minByOrNull {
                Math.abs(it.centerX() - near.centerX()) + Math.abs(it.centerY() - near.centerY())
            }
            else -> null
        }
    }

    /** اسم العنصر كما يراه المستخدم. لا يقرأ محتوى الحقول ولا كلمات المرور أبداً، ويخفي الأرقام الطويلة. */
    fun labelOf(node: AccessibilityNodeInfo): String {
        if (node.isPassword) return "password field"
        val raw: CharSequence? = if (node.isEditable) {
            node.hintText ?: node.contentDescription ?: node.labeledBy?.text
        } else {
            node.text ?: node.contentDescription
        }
        val s = raw?.toString()?.trim().orEmpty()
        if (s.isEmpty()) return if (node.isEditable) "unnamed field" else ""
        return Safety.redact(s.replace('\n', ' ')).take(50)
    }

    // ───────────── أسماء التطبيقات ─────────────

    private val appNames = HashMap<String, String>()

    fun appName(pkg: String): String {
        if (pkg.isEmpty()) return "App"
        appNames[pkg]?.let { return it }
        var name: String? = KNOWN_APPS[pkg]
        if (name == null) {
            name = try {
                val pm = ctx.packageManager
                @Suppress("DEPRECATION")
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().trim().ifEmpty { null }
            } catch (_: Throwable) {
                null
            }
        }
        if (name == null) name = fallbackName(pkg)
        appNames[pkg] = name
        return name
    }

    private fun fallbackName(pkg: String): String {
        val last = pkg.substringAfterLast('.')
        return if (last.isEmpty()) pkg else last.replaceFirstChar { it.uppercaseChar() }
    }

    private fun isBrowser(pkg: String): Boolean {
        val p = pkg.lowercase()
        return BROWSER_HINTS.any { p.contains(it) }
    }

    private fun clickTarget(node: AccessibilityNodeInfo, own: Rect, screenArea: Long): Pair<Rect, Boolean> {
        if (node.isClickable) return Pair(own, false)
        var p = node.parent
        var depth = 0
        while (p != null && depth < 3) {
            if (p.isClickable) {
                val r = Rect()
                p.getBoundsInScreen(r)
                val area = r.width().toLong() * r.height()
                return if (!r.isEmpty && area <= screenArea / 2) Pair(r, true) else Pair(own, false)
            }
            p = p.parent
            depth++
        }
        return Pair(own, false)
    }

    /** نوع العنصر بمصطلحات تعليمات الذكاء الاصطناعي نفسها. */
    private fun kindOf(node: AccessibilityNodeInfo, viaParent: Boolean): String {
        val cls = node.className?.toString().orEmpty()
        val actionable = node.isClickable || viaParent
        return when {
            node.isPassword -> "secret-field"
            node.isEditable -> if (node.isShowingHintText || node.text.isNullOrEmpty()) "field-empty" else "field-filled"
            node.isCheckable -> if (node.isChecked) "option-checked" else "option"
            cls.endsWith("Spinner") -> "dropdown"
            cls.contains("Image") && actionable -> "image-button"
            cls.contains("Image") -> "image"
            actionable -> "button"
            else -> "text"
        }
    }

    // ───────────── مرور واحد على الشجرة ─────────────

    private inner class Pass(
        val pkg: String,
        val includeUnnamed: Boolean,
        val collect: Boolean,
        val maxVisible: Int,
        val maxHidden: Int,
        budgetMs: Long
    ) {
        /** حدّ زمني صارم: لا نحجب الخيط الرئيسي أكثر من ذلك مهما كانت الشاشة ضخمة. */
        private val deadline = SystemClock.uptimeMillis() + budgetMs
        private val dm = ctx.resources.displayMetrics
        private val sw = dm.widthPixels
        private val sh = dm.heightPixels
        private val screenArea = sw.toLong() * sh
        private val browser = isBrowser(pkg)

        private val cands = ArrayList<Cand>()
        private val below = ArrayList<Pair<Int, String>>()
        private val above = ArrayList<Pair<Int, String>>()
        private var visCount = 0
        private var hidCount = 0
        private var unnamedCount = 0
        private var down = false
        private var up = false

        var site = ""
            private set
        var title = ""
            private set

        fun go(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            if (!collect && site.isNotEmpty() && title.isNotEmpty()) return
            if (SystemClock.uptimeMillis() > deadline) return
            val vis = n.isVisibleToUser
            if (vis) {
                if (visCount >= maxVisible) return
                visCount++
            } else {
                if (hidCount >= maxHidden) return
                hidCount++
            }
            val own = Rect()
            n.getBoundsInScreen(own)
            if (!own.isEmpty) {
                if (vis) visitVisible(n, own) else visitHidden(n, own)
            }
            for (i in 0 until n.childCount) {
                if (SystemClock.uptimeMillis() > deadline) return
                val c = n.getChild(i) ?: continue
                go(c, depth + 1)
            }
        }

        private fun visitVisible(n: AccessibilityNodeInfo, own: Rect) {
            detectPage(n, own)
            if (!collect) return
            if (n.isScrollable) noteScroller(n, own)
            if (cands.size >= MAX_CANDS) return
            val clickable = n.isClickable
            var label = labelOf(n)
            var unnamed = false
            if (label.isEmpty()) {
                if (includeUnnamed && clickable && unnamedCount < MAX_UNNAMED) {
                    label = UNNAMED
                    unnamed = true
                } else {
                    return
                }
            }
            val (rect0, viaParent) = clickTarget(n, own, screenArea)
            val clip = Rect(rect0)
            if (!clip.intersect(0, 0, sw, sh)) return
            if (cands.any { it.rect == clip }) return
            if (unnamed) unnamedCount++
            val partial = clip.height() * 10 < rect0.height() * 6 || clip.width() * 10 < rect0.width() * 6
            val editable = n.isEditable
            val kind = kindOf(n, viaParent)
            val sb = StringBuilder()
            if (!n.isEnabled) sb.append(" (disabled)")
            if (editable && n.isFocused) sb.append(" (focused field)")
            if (n.isSelected && (clickable || viaParent)) sb.append(" (selected)")
            if (partial) sb.append(" (partly visible)")
            val idHint = if (unnamed) idHintOf(n) else ""
            cands += Cand(
                label, kind, clip, editable, n.isEnabled, editable && n.isFocused,
                editable || clickable || viaParent || n.isCheckable, sb.toString(), idHint
            )
        }

        private fun idHintOf(n: AccessibilityNodeInfo): String {
            val id = n.viewIdResourceName ?: return ""
            val short = id.substringAfter(":id/", "")
            return if (short.isEmpty() || short.length > 30) "" else " {$short}"
        }

        /** عنصر تفاعلي خارج الشاشة: نحفظه ليقول الذكاء «مرّر للأسفل» بدل أن يخمّن. */
        private fun visitHidden(n: AccessibilityNodeInfo, own: Rect) {
            if (!collect) return
            if (!(n.isClickable || n.isEditable || n.isCheckable)) return
            val side = Regions.offscreenSide(own.top, own.bottom, own.left, own.right, sw, sh)
            if (side == 0) return
            val label = labelOf(n)
            if (label.isEmpty()) return
            val dist = if (side > 0) own.top - sh else -own.bottom
            if (dist > sh * 2) return
            val what = if (n.isEditable) "field" else "button"
            val text = (if (side > 0) "↓ " else "↑ ") + "[$what] $label"
            (if (side > 0) below else above) += Pair(dist, text)
        }

        private fun detectPage(n: AccessibilityNodeInfo, own: Rect) {
            if (site.isEmpty()) {
                val id = n.viewIdResourceName
                if (id != null && URL_IDS.any { id.endsWith(it) }) {
                    site = Regions.siteOf(n.text?.toString().orEmpty())
                } else if (browser && n.isEditable && own.top < sh / 4) {
                    site = Regions.siteOf(n.text?.toString().orEmpty())
                }
            }
            if (title.isEmpty() && n.className?.toString() == "android.webkit.WebView") {
                val t = n.text?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) title = Safety.redact(t.replace('\n', ' ')).take(80)
            }
        }

        private fun noteScroller(n: AccessibilityNodeInfo, own: Rect) {
            val area = own.width().toLong() * own.height()
            if (area * 100 < screenArea * 35) return
            val acts = n.actionList
            val hasDown = acts.contains(AccessibilityAction.ACTION_SCROLL_DOWN)
            val hasUp = acts.contains(AccessibilityAction.ACTION_SCROLL_UP)
            val hasFwd = acts.contains(AccessibilityAction.ACTION_SCROLL_FORWARD)
            val hasBack = acts.contains(AccessibilityAction.ACTION_SCROLL_BACKWARD)
            val horiz = acts.contains(AccessibilityAction.ACTION_SCROLL_LEFT) || acts.contains(AccessibilityAction.ACTION_SCROLL_RIGHT)
            if (hasDown || (hasFwd && !horiz)) down = true
            if (hasUp || (hasBack && !horiz)) up = true
        }

        private fun windowTitle(root: AccessibilityNodeInfo, appName: String): String {
            return try {
                val t = root.window?.title?.toString()?.trim().orEmpty()
                if (t.isEmpty() || t == appName) "" else Safety.redact(t).take(80)
            } catch (_: Throwable) {
                ""
            }
        }

        fun page(root: AccessibilityNodeInfo): PageInfo {
            val name = appName(pkg)
            val t = if (title.isNotEmpty()) title else windowTitle(root, name)
            return PageInfo(pkg, name, site, t)
        }

        /** عند كثرة العناصر نُبقي كل التفاعلية أولاً ثم نملأ بالنصوص، ونحافظ على ترتيب القراءة. */
        private fun select(): List<Cand> {
            if (cands.size <= MAX_ELEMENTS) return cands
            val chosen = HashSet<Int>()
            for ((i, c) in cands.withIndex()) {
                if (c.interactive && chosen.size < MAX_INTERACTIVE) chosen += i
            }
            for (i in cands.indices) {
                if (chosen.size >= MAX_ELEMENTS) break
                chosen += i
            }
            return cands.filterIndexed { i, _ -> i in chosen }
        }

        fun finish(root: AccessibilityNodeInfo): ScreenModel {
            val chosen = select()
            val els = ArrayList<El>(chosen.size)
            val lines = ArrayList<String>(chosen.size)
            val rects = ArrayList<Rect>(chosen.size)
            var interactive = 0
            for ((i, c) in chosen.withIndex()) {
                val place = Regions.placeOf(c.rect.centerX(), c.rect.centerY(), sw, sh)
                val el = El(i, c.kind, c.label, place, c.editable, c.enabled, c.focused)
                els += el
                lines += "$i: [${c.kind}] ${c.label}${c.suffix}${c.idHint} @$place"
                rects += c.rect
                if (el.interactive) interactive++
            }
            val offscreen = ArrayList<String>()
            below.sortBy { it.first }
            above.sortBy { it.first }
            below.map { it.second }.distinct().take(MAX_OFFSCREEN_PER_SIDE).forEach { offscreen += it }
            above.map { it.second }.distinct().take(MAX_OFFSCREEN_PER_SIDE).forEach { offscreen += it }
            val canDown = down || below.isNotEmpty()
            val canUp = up || above.isNotEmpty()
            return ScreenModel(page(root), els, lines, rects, canDown, canUp, offscreen, interactive, SystemClock.uptimeMillis())
        }
    }

    companion object {
        const val UNNAMED = "(unnamed)"
        private const val MAX_DEPTH = 45
        private const val MAX_VISIBLE = 900
        private const val MAX_HIDDEN = 450
        private const val PEEK_VISIBLE = 350
        private const val MAX_CANDS = 400
        private const val MAX_ELEMENTS = 90
        private const val MAX_INTERACTIVE = 75
        private const val MAX_UNNAMED = 25
        private const val READ_BUDGET_MS = 1800L
        private const val PEEK_BUDGET_MS = 400L
        private const val FIND_BUDGET_MS = 700L
        private const val MAX_OFFSCREEN_PER_SIDE = 6

        private val URL_IDS = listOf(
            ":id/url_bar", ":id/location_bar_edit_text", ":id/mozac_browser_toolbar_url_view",
            ":id/url_bar_title", ":id/display_url", ":id/url_field", ":id/omnibarTextInput"
        )

        private val BROWSER_HINTS = listOf(
            "chrome", "firefox", "browser", "opera", "emmx", "brave", "vivaldi", "duckduckgo", "sbrowser", "kiwi"
        )

        private val KNOWN_APPS = mapOf(
            "com.android.chrome" to "Chrome",
            "com.chrome.beta" to "Chrome",
            "com.sec.android.app.sbrowser" to "Samsung Internet",
            "org.mozilla.firefox" to "Firefox",
            "com.microsoft.emmx" to "Edge",
            "com.brave.browser" to "Brave",
            "com.opera.browser" to "Opera",
            "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.photos" to "Google Photos",
            "com.sec.android.gallery3d" to "Gallery",
            "com.android.settings" to "Settings",
            "com.google.android.googlequicksearchbox" to "Google",
            "com.google.android.gm" to "Gmail",
            "com.android.vending" to "Play Store",
            "com.google.android.youtube" to "YouTube",
            "com.google.android.apps.maps" to "Google Maps",
            "com.instagram.android" to "Instagram",
            "com.facebook.katana" to "Facebook",
            "com.sec.android.app.myfiles" to "My Files",
            "com.google.android.documentsui" to "Files",
            "com.android.documentsui" to "Files",
            "com.sec.android.app.launcher" to "Home screen",
            "com.android.launcher3" to "Home screen",
            "com.google.android.apps.nexuslauncher" to "Home screen"
        )
    }
}
