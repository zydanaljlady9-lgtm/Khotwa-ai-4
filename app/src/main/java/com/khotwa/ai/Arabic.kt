package com.khotwa.ai

/**
 * مطابقة كلام المستخدم مع أسماء العناصر بلا شبكة: تطبيع النص العربي واللاتيني، وحذف «ال» وكلمات الحشو،
 * ونية الجملة (دخول، تسجيل، التالي…) بالعربية والإنجليزية. لا يعتمد على أندرويد فتُختبر وحدته في CI.
 */
object Arabic {

    private val SPACES = Regex("\\s+")

    /** حذف التشكيل والتطويل، وتوحيد الألف والياء والتاء المربوطة والأرقام، وتحويل الرموز إلى مسافات. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (raw in s.lowercase()) {
            val c: Char = when (raw) {
                in 'ً'..'ٟ', 'ٰ', 'ـ' -> continue
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى', 'ئ' -> 'ي'
                'ؤ' -> 'و'
                'ة' -> 'ه'
                in '٠'..'٩' -> '0' + (raw - '٠')
                in '۰'..'۹' -> '0' + (raw - '۰')
                else -> raw
            }
            sb.append(if (c.isLetterOrDigit() || Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }) c else ' ')
        }
        return sb.toString().trim().replace(SPACES, " ")
    }

    /** يحذف «ال» وما يلتصق بها من أول الكلمة، وجمع الإنجليزية البسيط. */
    fun stem(t: String): String {
        var x = t
        if (x.length > 4 && (x.startsWith("وال") || x.startsWith("بال") || x.startsWith("كال") || x.startsWith("فال"))) {
            x = x.substring(3)
        } else if (x.length > 3 && x.startsWith("ال")) {
            x = x.substring(2)
        } else if (x.length > 4 && x.startsWith("لل")) {
            x = x.substring(2)
        }
        if (x.length > 4 && x.all { it in 'a'..'z' } && x.endsWith("s") && !x.endsWith("ss")) {
            x = x.dropLast(1)
        }
        return x
    }

    private val STOP: Set<String> = listOf(
        "هل", "ماذا", "لماذا", "كيف", "اين", "وين", "متى", "من", "في", "على", "الى", "عن", "مع",
        "هذا", "هذه", "هذي", "ذلك", "هنا", "هناك", "لو", "لي", "لنا", "انا", "انت", "اريد", "ابغى", "ابغي",
        "ابي", "بغيت", "وش", "ايش", "شو", "ممكن", "سمحت", "اضغط", "انقر", "ضغط", "زر", "الزر", "خانه", "حقل",
        "the", "a", "an", "to", "of", "and", "or", "is", "it", "for", "on", "in", "this", "that", "please",
        "button", "click", "tap", "press", "go", "i", "me", "my", "want", "do", "what", "how", "where"
    ).map { normalize(it) }.toSet()

    /** كلمات الجملة بعد التطبيع وحذف «ال» وكلمات الحشو. */
    fun tokens(s: String): List<String> =
        normalize(s).split(' ').filter { it.isNotEmpty() }.map { stem(it) }.filter { it.isNotEmpty() && it !in STOP }

    enum class Intent(val phrases: List<String>, val words: List<String>) {
        LOGIN(listOf("تسجيل الدخول", "تسجيل دخول", "سجل دخول", "sign in", "log in"), listOf("دخول", "login", "signin", "logon")),
        REGISTER(
            listOf("إنشاء حساب", "حساب جديد", "فتح حساب", "sign up", "create account", "create an account", "join now"),
            listOf("register", "signup", "اشتراك", "اشترك")
        ),
        NEXT(emptyList(), listOf("التالي", "next", "continue", "proceed", "متابعة", "استمرار", "استمر", "تابع", "اكمل", "كمل")),
        BACK(emptyList(), listOf("رجوع", "ارجع", "عودة", "عد", "السابق", "back", "previous", "prev")),
        SUBMIT(listOf("تقديم الطلب", "تقديم طلب"), listOf("إرسال", "ارسل", "تقديم", "قدم", "تقدم", "submit", "send", "apply")),
        SAVE(emptyList(), listOf("حفظ", "احفظ", "save")),
        SEARCH(emptyList(), listOf("بحث", "ابحث", "search", "find")),
        CANCEL(emptyList(), listOf("إلغاء", "الغي", "تراجع", "cancel")),
        CLOSE(emptyList(), listOf("إغلاق", "اغلق", "اقفل", "close", "dismiss")),
        DOWNLOAD(emptyList(), listOf("تحميل", "حمل", "نزل", "تنزيل", "download")),
        UPLOAD(listOf("choose file", "اختيار ملف", "اختر ملف"), listOf("رفع", "ارفع", "إرفاق", "ارفق", "upload", "attach", "browse")),
        CONFIRM(emptyList(), listOf("تأكيد", "اكد", "موافق", "اوافق", "ok", "okay", "confirm", "accept", "agree", "حسنا", "تم")),
        MENU(emptyList(), listOf("قائمة", "menu")),
        SETTINGS(emptyList(), listOf("إعدادات", "اعداد", "settings", "setting")),
        SKIP(emptyList(), listOf("تخطي", "تخط", "skip", "لاحقا", "later")),
        FINISH(emptyList(), listOf("إنهاء", "انهي", "finish", "done", "اتمام")),
        ADD(emptyList(), listOf("إضافة", "اضف", "add")),
        EDIT(emptyList(), listOf("تعديل", "عدل", "edit")),
        DELETE(emptyList(), listOf("حذف", "احذف", "delete", "remove", "مسح", "امسح")),
        SHARE(emptyList(), listOf("مشاركة", "شارك", "share")),
        HELP(emptyList(), listOf("مساعدة", "help", "دعم", "support"))
    }

    private class IntentKeys(val phrases: List<String>, val words: Set<String>)

    private val INTENT_KEYS: Map<Intent, IntentKeys> = Intent.values().associateWith { i ->
        IntentKeys(i.phrases.map { normalize(it) }, i.words.map { stem(normalize(it)) }.toSet())
    }

    private val LOGIN_OR_REGISTER_WORDS: Set<String> = listOf("تسجيل", "سجل", "اسجل", "سجلني", "تسجل").map { stem(normalize(it)) }.toSet()

    fun intentsOf(text: String): Set<Intent> {
        val norm = normalize(text)
        if (norm.isEmpty()) return emptySet()
        val toks = norm.split(' ').filter { it.isNotEmpty() }.map { stem(it) }.toSet()
        val padded = " $norm "
        val out = mutableSetOf<Intent>()
        for (i in Intent.values()) {
            val k = INTENT_KEYS.getValue(i)
            if (k.phrases.any { padded.contains(" $it ") } || k.words.any { it in toks }) out += i
        }
        if (Intent.LOGIN !in out && Intent.REGISTER !in out && toks.any { it in LOGIN_OR_REGISTER_WORDS }) {
            out += Intent.LOGIN
            out += Intent.REGISTER
        }
        return out
    }

    /**
     * درجة تشابه بين كلام المستخدم واسم عنصر (0..1).
     * 1.0 إن ذكر المستخدم اسم العنصر كاملاً، 0.85 إن تطابقت الوظيفة (مثل «ابحث» و«Search»).
     */
    fun similarity(query: String, label: String): Double {
        val qn = normalize(query)
        val ln = normalize(label)
        if (qn.isEmpty() || ln.isEmpty()) return 0.0
        if (ln.length >= 2 && " $qn ".contains(" $ln ")) return 1.0
        val qt = tokens(query).toSet()
        val lt = tokens(label)
        var score = 0.0
        if (lt.isNotEmpty() && qt.isNotEmpty()) {
            var hits = 0.0
            for (t in lt) {
                hits += when {
                    t in qt -> 1.0
                    t.length >= 4 && qt.any { it.length >= 4 && (it.startsWith(t) || t.startsWith(it)) } -> 0.7
                    else -> 0.0
                }
            }
            score = hits / lt.size
            // كلمة واحدة من تسمية طويلة لا تكفي للجزم
            if (lt.size >= 3 && hits < 2.0) score = minOf(score, 0.4)
        }
        val qi = intentsOf(query)
        if (qi.isNotEmpty() && qi.intersect(intentsOf(label)).isNotEmpty()) score = maxOf(score, 0.85)
        return minOf(score, 1.0)
    }

    private val WHERE_PHRASES: List<String> = listOf(
        "اين انا", "وين انا", "اين اكون", "في اي صفحه", "في اي موقع", "في اي تطبيق", "اي صفحه هذي", "اي صفحه هذه",
        "where am i", "what page", "which page", "where are we"
    ).map { normalize(it) }

    /** هل يسأل المستخدم «أين أنا؟» */
    fun isWhereAmI(q: String): Boolean {
        val n = normalize(q)
        return WHERE_PHRASES.any { n.contains(it) }
    }

    enum class FieldKind { PASSWORD, ID, CARD, OTP, EMAIL, PHONE, BIRTH, DATE, NAME, COUNTRY, ADDRESS, MESSAGE, SEARCH, OTHER }

    private fun keys(vararg w: String): Set<String> = w.map { stem(normalize(it)) }.toSet()

    private val K_PASSWORD = keys("مرور", "سر", "password", "pass", "passcode", "pin", "سري")
    private val K_ID = keys("هوية", "جواز", "passport", "iqama", "إقامة", "national", "قومي", "ssn", "id")
    private val K_CARD = keys("بطاقة", "card", "cvv", "cvc", "ائتمان", "visa", "mastercard", "credit")
    private val K_POSTAL = keys("بريدي", "zip", "postal")
    private val K_OTP = keys("otp", "تحقق", "verification", "verify", "رمز", "code")
    private val K_EMAIL = keys("بريد", "ايميل", "email", "mail", "gmail")
    private val K_PHONE = keys("هاتف", "جوال", "موبايل", "phone", "mobile", "tel", "telephone", "واتساب", "whatsapp")
    private val K_BIRTH = keys("ميلاد", "birth", "birthday", "dob")
    private val K_DATE = keys("تاريخ", "date", "يوم", "شهر", "سنة", "day", "month", "year")
    private val K_NAME = keys("اسم", "name", "fullname", "first", "last", "surname", "لقب", "عائلة", "family")
    private val K_COUNTRY = keys("دولة", "بلد", "country", "جنسية", "nationality")
    private val K_ADDRESS = keys("عنوان", "address", "مدينة", "city", "شارع", "street", "منطقة", "region", "state", "محافظة")
    private val K_MESSAGE = keys("رسالة", "message", "تعليق", "comment", "ملاحظات", "notes", "وصف", "description", "سبب", "reason")
    private val K_SEARCH = keys("بحث", "search", "ابحث")

    /** نوع الخانة من اسمها، ليُشرح لصاحبها ما يُكتب فيها. */
    fun fieldKindOf(label: String): FieldKind {
        val t = tokens(label).toSet()
        fun has(k: Set<String>) = t.any { it in k }
        return when {
            has(K_PASSWORD) -> FieldKind.PASSWORD
            has(K_ID) -> FieldKind.ID
            has(K_CARD) -> FieldKind.CARD
            has(K_POSTAL) -> FieldKind.ADDRESS
            has(K_OTP) -> FieldKind.OTP
            has(K_EMAIL) -> FieldKind.EMAIL
            has(K_PHONE) -> FieldKind.PHONE
            has(K_BIRTH) -> FieldKind.BIRTH
            has(K_DATE) -> FieldKind.DATE
            has(K_NAME) -> FieldKind.NAME
            has(K_COUNTRY) -> FieldKind.COUNTRY
            has(K_ADDRESS) -> FieldKind.ADDRESS
            has(K_MESSAGE) -> FieldKind.MESSAGE
            has(K_SEARCH) -> FieldKind.SEARCH
            else -> FieldKind.OTHER
        }
    }
}
