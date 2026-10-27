package com.khotwa.ai

/**
 * عقل خُطوة المحلي: يعمل بلا شبكة على عناصر الشاشة وأسمائها وأماكنها، وبكل لغات التطبيق.
 * يطابق كلام المستخدم مع العناصر (Arabic.similarity)، ويشرح الخانات، ويجيب «أين أنا؟»،
 * ويمرّر، ويقترح الخطوة التالية، ولا يخمّن بثقة عالية. لا يفهم الصور؛ الصور تحتاج النموذج السحابي أو Gemini Nano.
 *
 * plan() دالة نقية تُرجع «ماذا أقول» كمفتاح (Say) بدل النص، فتُختبر في CI بلا جهاز؛ render() يحوّله للغة المستخدم.
 */
object OfflineGuideEngine {
    const val ENGINE = "Khotwa Local"

    // كلمات النوايا بعدة لغات (كما كانت في الإصدار 3)
    internal val NEXT = listOf(
        "التالي", "متابعة", "استمرار", "تابع", "حفظ", "إرسال", "ارسال", "تأكيد", "دخول", "تسجيل الدخول", "بحث", "فتح", "تحميل", "موافق", "تقديم", "ابدأ", "قدّم",
        "next", "continue", "submit", "save", "ok", "sign in", "log in", "login", "search", "apply", "start", "confirm", "send", "done", "open", "download",
        "suivant", "continuer", "valider", "envoyer", "enregistrer", "connexion", "rechercher", "postuler", "commencer",
        "siguiente", "continuar", "enviar", "guardar", "iniciar", "buscar", "aplicar", "próximo", "avançar", "salvar", "entrar",
        "ileri", "devam", "gönder", "kaydet", "giriş", "ara", "başvur", "başla", "tamam",
        "berikutnya", "lanjut", "kirim", "simpan", "masuk", "cari", "daftar", "mulai",
        "आगे", "जारी", "भेजें", "सहेजें", "लॉग इन", "खोजें", "اگلا", "جاری", "بھیجیں", "محفوظ", "تلاش"
    )
    internal val BACK = listOf(
        "رجوع", "عودة", "السابق", "إلغاء", "الغاء", "إغلاق", "back", "cancel", "close", "previous",
        "retour", "annuler", "fermer", "atrás", "cancelar", "cerrar", "voltar", "fechar", "geri", "iptal", "kapat",
        "kembali", "batal", "tutup", "वापस", "रद्द", "बंद", "واپس", "منسوخ", "بند"
    )
    internal val FORM = listOf(
        "خانة", "حقل", "نموذج", "أكتب", "اكتب", "تعبئة", "ماذا أكتب", "وش أكتب", "ايش اكتب",
        "field", "form", "type", "write", "fill", "champ", "formulaire", "écrire", "remplir", "campo", "formulario", "escribir", "rellenar",
        "preencher", "escrever", "alan", "yaz", "doldur", "kolom", "isi", "tulis", "फ़ील्ड", "फॉर्म", "लिखें", "भरें", "خانہ", "فارم", "لکھ", "بھر"
    )
    private val QUESTION = listOf("ماذا", "كيف", "ايش", "وش", "what", "how", "quoi", "comment", "qué", "cómo", "o que", "como", "ne", "nasıl", "apa", "bagaimana", "क्या", "कैसे", "کیا", "کیسے")
    internal val STOP = listOf("توقف", "خلاص", "انتهيت", "stop", "done", "arrête", "para", "pare", "dur", "berhenti", "रुको", "روکو", "بس")
    private val GOAL = listOf("أريد", "ابغى", "ابي", "كيف", "أين", "i want", "how", "where", "je veux", "comment", "quiero", "cómo", "quero", "istiyorum", "nasıl", "saya mau", "ingin", "मुझे", "कैसे", "مجھے", "کیسے")
    private val START = listOf("ابدأ", "إرشاد", "ارشاد", "شاشة", "start", "guide", "screen", "commencer", "guider", "empezar", "guía", "começar", "başla", "mulai", "शुरू", "شروع")

    // عبارات إضافية لهذا المحرك
    private val ASK_NEXT = listOf(
        "what now", "what next", "next step", "what do i do", "what should i do", "and then", "then what",
        "ماذا بعد", "ماذا افعل", "وش بعد", "ايش بعد", "الخطوه التاليه", "ما التالي", "بعدين", "ثم ماذا", "وبعدين"
    )
    private val SCROLL_DOWN = listOf("scroll down", "scroll", "down", "مرر", "انزل", "تحت", "اسفل", "للاسفل", "descend", "aşağı", "bawah", "नीचे", "نیچے")
    private val SCROLL_UP = listOf("scroll up", "up", "اصعد", "فوق", "اعلى", "للاعلى", "yukarı", "atas", "ऊपर", "اوپر")
    private val NEXT_PRIORITY = listOf(Arabic.Intent.NEXT, Arabic.Intent.SUBMIT, Arabic.Intent.CONFIRM, Arabic.Intent.SAVE, Arabic.Intent.FINISH)

    /** ما يقوله المحرك، كمفتاح يُترجم لاحقاً. */
    internal enum class Say {
        EMPTY, STOP, WHERE, NO_ELEMENTS, PRESS, FIELD, GUESS, NEXT_STEP,
        SCROLL_DOWN, SCROLL_UP, END_OF_PAGE, TOP_OF_PAGE, SECRET_FIELD, UNSURE, CLARIFY
    }

    /** خطة الرد: ماذا نقول، وأي عنصر نشير إليه، ودرجة الثقة، وهل انتهى الإرشاد، وهل نمرّر. */
    internal data class Plan(
        val say: Say,
        val target: El? = null,
        val arg: String = "",
        val conf: Double,
        val done: Boolean = false,
        val scroll: String? = null,
        val options: List<String> = emptyList(),
        val weak: Boolean = false,
        val fieldKind: Arabic.FieldKind? = null
    )

    // ───────────── الواجهات العامة ─────────────

    /** مع شاشة كاملة (المسار الحقيقي). */
    fun guide(question: String, screen: ScreenModel, auto: Boolean = false): Result<AiReply> =
        Result.success(render(plan(question, screen.where(), screen.els, screen.canScrollDown, screen.canScrollUp, screen.weak, auto)))

    /** مع أسطر نصية كما تأتي من الذكاء (توافق مع الاختبارات والمسارات القديمة). */
    fun guide(question: String, pkg: String, elements: List<String>): Result<AiReply> {
        val els = elements.mapNotNull(::parse).map { El(it.id, it.kind, it.label, "", it.editable) }
        return Result.success(render(plan(question, pkg, els, false, false, false, false)))
    }

    // ───────────── المنطق النقي ─────────────

    internal fun plan(
        question: String,
        where: String,
        els: List<El>,
        canDown: Boolean,
        canUp: Boolean,
        weak: Boolean,
        auto: Boolean
    ): Plan {
        val q = question.trim()
        val qn = Arabic.normalize(q)
        val toks = Arabic.tokens(q).toSet()
        if (!auto && qn.isEmpty()) return Plan(Say.EMPTY, conf = .95)
        if (!auto && isStop(q)) return Plan(Say.STOP, conf = .99, done = true)
        if (!auto && Arabic.isWhereAmI(q)) return Plan(Say.WHERE, arg = where, conf = .95, weak = weak)
        if (els.isEmpty()) return Plan(Say.NO_ELEMENTS, conf = .82)

        // 1) اسم العنصر الذي يذكره المستخدم
        var best: El? = null
        var bestScore = 0.0
        if (!auto) {
            for (e in els) {
                if (!e.enabled || e.secret) continue
                var s = Arabic.similarity(q, e.label)
                if (s <= 0.0) continue
                if (e.interactive) s += 0.05
                if (s > bestScore) {
                    best = e
                    bestScore = s
                }
            }
            if (best != null && bestScore >= 0.8) {
                val b = best
                return if (b.editable) {
                    Plan(Say.FIELD, b, b.label, .9, fieldKind = Arabic.fieldKindOf(b.label))
                } else {
                    Plan(Say.PRESS, b, b.label, .9)
                }
            }
        }

        // 2) التمرير
        if (!auto) {
            if (SCROLL_DOWN.any { hasTerm(qn, toks, it) }) {
                return if (canDown) Plan(Say.SCROLL_DOWN, conf = .85, scroll = "down") else Plan(Say.END_OF_PAGE, conf = .8)
            }
            if (SCROLL_UP.any { hasTerm(qn, toks, it) }) {
                return if (canUp) Plan(Say.SCROLL_UP, conf = .85, scroll = "up") else Plan(Say.TOP_OF_PAGE, conf = .8)
            }
        }

        // 3) سؤال عن خانة في نموذج
        val fields = els.filter { it.editable && !it.secret }
        val asksAboutField = FORM.any { hasTerm(qn, toks, it) }
        if (!auto && (asksAboutField || (fields.isNotEmpty() && QUESTION.any { hasTerm(qn, toks, it) }))) {
            val wanted = Arabic.fieldKindOf(q)
            if (wanted == Arabic.FieldKind.PASSWORD || wanted == Arabic.FieldKind.OTP || wanted == Arabic.FieldKind.CARD) {
                return Plan(Say.SECRET_FIELD, conf = .9)
            }
            if (fields.isEmpty()) return Plan(Say.CLARIFY, conf = .5)
            val f = pickField(q, fields)
            return Plan(Say.FIELD, f, f.label, .88, fieldKind = Arabic.fieldKindOf(f.label))
        }

        // 4) تخمين مقبول: نشير إليه ونسأل
        if (best != null && bestScore >= 0.45) {
            return Plan(Say.GUESS, best, best.label, .66)
        }

        // 5) ماذا بعد؟
        val askingNext = auto || Arabic.Intent.NEXT in Arabic.intentsOf(q) || ASK_NEXT.any { hasTerm(qn, toks, it) }
        if (askingNext) return nextStep(els)

        // 6) لا أخمّن: أعرض ما أراه
        val options = els.filter { it.interactive && !it.editable && it.enabled && !it.secret }.take(3).map { it.label }
        return if (options.isNotEmpty()) Plan(Say.UNSURE, conf = .4, options = options) else Plan(Say.CLARIFY, conf = .3)
    }

    private fun nextStep(els: List<El>): Plan {
        val empty = els.firstOrNull { it.editable && !it.secret && it.kind == "field-empty" && it.enabled }
        if (empty != null) return Plan(Say.FIELD, empty, empty.label, .8, fieldKind = Arabic.fieldKindOf(empty.label))
        for (intent in NEXT_PRIORITY) {
            val e = els.firstOrNull { it.enabled && !it.editable && !it.secret && intent in Arabic.intentsOf(it.label) }
            if (e != null) return Plan(Say.NEXT_STEP, e, e.label, .8)
        }
        return Plan(Say.CLARIFY, conf = .5)
    }

    private fun pickField(q: String, fields: List<El>): El {
        var named: El? = null
        var namedScore = 0.0
        for (f in fields) {
            val s = Arabic.similarity(q, f.label)
            if (s > namedScore) {
                named = f
                namedScore = s
            }
        }
        if (named != null && namedScore >= 0.6) return named
        val qk = Arabic.fieldKindOf(q)
        if (qk != Arabic.FieldKind.OTHER) {
            fields.firstOrNull { Arabic.fieldKindOf(it.label) == qk }?.let { return it }
        }
        return fields.firstOrNull { it.focused } ?: fields.firstOrNull { it.kind == "field-empty" } ?: fields.first()
    }

    private fun isStop(q: String): Boolean {
        val words = Arabic.normalize(q).split(' ').filter { it.isNotEmpty() }
        if (words.size > 3) return false
        val qn = Arabic.normalize(q)
        val toks = Arabic.tokens(q).toSet() + words
        return STOP.any { hasTerm(qn, toks, it) }
    }

    /**
     * يطابق مصطلحاً كاملاً: عبارة متعددة الكلمات تُبحث كعبارة، والكلمة المفردة تُقارن بالجذور،
     * فلا تطابق «بس» داخل «بسيط» ولا «down» داخل «download».
     */
    private fun hasTerm(qn: String, toks: Set<String>, term: String): Boolean {
        val n = Arabic.normalize(term)
        if (n.isEmpty()) return false
        return if (n.contains(' ')) " $qn ".contains(" $n ") else (n in toks || Arabic.stem(n) in toks)
    }

    /** مطابقة كلمة كاملة للكلمات اللاتينية القصيرة، ومطابقة جزئية للعربية. */
    private fun containsWord(text: String, word: String): Boolean {
        if (word.isEmpty()) return false
        val w = word.lowercase()
        return if (w.length <= 3 && w.all { it.isLetter() && it.code < 128 }) {
            Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(text)
        } else {
            text.contains(w)
        }
    }

    // ───────────── تحويل الخطة إلى كلام بلغة المستخدم ─────────────

    internal fun render(p: Plan): AiReply {
        val t = p.target
        val where = if (t == null || t.place.isEmpty()) "" else L.f(R.string.off_at_place, Regions.placeName(t.place))
        val say = when (p.say) {
            Say.EMPTY -> L.s(R.string.off_empty)
            Say.STOP -> L.s(R.string.off_stop)
            Say.WHERE -> L.f(R.string.off_where, p.arg) + if (p.weak) " " + L.s(R.string.off_where_weak) else ""
            Say.NO_ELEMENTS -> L.s(R.string.off_no_elements)
            Say.PRESS -> L.f(R.string.off_press, p.arg) + where
            Say.FIELD -> fieldHelp(p.fieldKind ?: Arabic.FieldKind.OTHER, t?.label.orEmpty())
            Say.GUESS -> L.f(R.string.off_guess, p.arg) + where
            Say.NEXT_STEP -> L.f(R.string.off_next_step, p.arg) + where
            Say.SCROLL_DOWN -> L.s(R.string.off_scroll_down)
            Say.SCROLL_UP -> L.s(R.string.off_scroll_up)
            Say.END_OF_PAGE -> L.s(R.string.off_end_of_page)
            Say.TOP_OF_PAGE -> L.s(R.string.off_top_of_page)
            Say.SECRET_FIELD -> L.s(R.string.off_secret_field)
            Say.UNSURE -> L.f(R.string.off_unsure_list, p.options.joinToString(", "))
            Say.CLARIFY -> L.s(R.string.off_clarify)
        }
        return AiReply(say, t?.id, p.conf, p.done, ENGINE, p.scroll)
    }

    /** شرح الخانة بلغة المستخدم. كلمات المرور والرموز وأرقام البطاقات لا تُشرح إلا بتنبيه «اكتبها بنفسك». */
    internal fun fieldHelp(kind: Arabic.FieldKind, label: String): String = when (kind) {
        Arabic.FieldKind.NAME -> L.f(R.string.fh_name, label)
        Arabic.FieldKind.EMAIL -> L.f(R.string.fh_email, label)
        Arabic.FieldKind.PHONE -> L.f(R.string.fh_phone, label)
        Arabic.FieldKind.COUNTRY -> L.f(R.string.fh_country, label)
        Arabic.FieldKind.BIRTH -> L.f(R.string.fh_birth, label)
        Arabic.FieldKind.DATE -> L.f(R.string.fh_date, label)
        Arabic.FieldKind.ADDRESS -> L.f(R.string.fh_address, label)
        Arabic.FieldKind.ID -> L.f(R.string.fh_id, label)
        Arabic.FieldKind.MESSAGE -> L.f(R.string.fh_message, label)
        Arabic.FieldKind.SEARCH -> L.f(R.string.fh_search, label)
        Arabic.FieldKind.PASSWORD, Arabic.FieldKind.OTP, Arabic.FieldKind.CARD -> L.s(R.string.off_secret_field)
        Arabic.FieldKind.OTHER -> L.f(R.string.off_field, if (label.isBlank()) L.s(R.string.off_this_field) else label)
    }

    // ───────────── الدردشة داخل التطبيق دون شبكة (كما في الإصدار 3) ─────────────

    fun chat(history: List<Turn>, @Suppress("UNUSED_PARAMETER") status: String): ChatReply {
        val last = history.lastOrNull { it.role == "user" }
        val q = last?.text?.trim()?.lowercase().orEmpty()
        val text = when {
            q.isBlank() -> L.s(R.string.off_chat_blank)
            last?.image != null -> L.s(R.string.off_chat_image)
            q.containsAny(START) -> L.s(R.string.off_chat_goal)
            q.containsAny(GOAL) -> L.s(R.string.off_chat_goal)
            else -> L.s(R.string.off_chat_default)
        }
        val actions = if (q.containsAny(START) || q.containsAny(GOAL)) {
            listOf(Action("start_guide", L.s(R.string.card_guide_title), goal = last?.text.orEmpty().take(120)))
        } else {
            emptyList()
        }
        return ChatReply(text, actions, ENGINE)
    }

    internal data class E(val id: Int, val kind: String, val label: String, val editable: Boolean, val secret: Boolean)

    internal fun parse(line: String): E? {
        val id = Regex("^(\\d+):").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val kind = Regex("\\[([^]]+)]").find(line)?.groupValues?.getOrNull(1)?.lowercase() ?: "text"
        val label = line.substringAfter("] ", "").trim()
        if (label.isBlank()) return null
        return E(id, kind, label, kind.contains("field"), kind.startsWith("secret"))
    }

    private fun String.containsAny(words: List<String>) = words.any { containsWord(this, it) }
}
