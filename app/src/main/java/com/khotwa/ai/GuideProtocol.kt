package com.khotwa.ai

import org.json.JSONObject

/** أجزاء بروتوكول الإرشاد: إنقاذ الرد المكسور، وقراءة التمرير وترقيم الخطوات، وتسميتها. */
object GuideProtocol {

    private val SAY_RE = Regex("\"say\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val TARGET_RE = Regex("\"target_id\"\\s*:\\s*(\\d+)")

    /** إن كان الرد JSON مكسوراً نلتقط الجملة والرقم بالتعبيرات النمطية بدل رمي الرد كله. say فارغ إن لم نفهم شيئاً. */
    fun rescue(content: String, engine: String): AiReply {
        val say = SAY_RE.find(content)?.groupValues?.get(1)
        if (say != null) {
            val clean = say.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\").trim()
            val id = TARGET_RE.find(content)?.groupValues?.get(1)?.toIntOrNull()
            return AiReply(clean, id, if (id != null) 0.7 else 0.0, false, engine)
        }
        val t = content.trim()
        // نص فارغ يعني: لم نفهم، والمستدعي يترجم الرسالة إلى لغة المستخدم.
        if (t.isEmpty() || t.startsWith("{") || t.contains("\"say\"") || t.contains("target_id")) {
            return AiReply("", null, 0.0, false, engine)
        }
        return AiReply(t.take(300), null, 0.0, false, engine)
    }

    /** "down" أو "up" أو null، مع قبول كلمات عربية شائعة. */
    fun scrollOf(j: JSONObject): String? {
        if (!j.has("scroll") || j.isNull("scroll")) return null
        return when (j.optString("scroll").trim().lowercase()) {
            "down", "اسفل", "أسفل", "تحت", "للاسفل", "للأسفل" -> "down"
            "up", "اعلى", "أعلى", "فوق", "للاعلى", "للأعلى" -> "up"
            else -> null
        }
    }

    /** رقم موجب بين 1 و99، أو null. */
    fun positive(j: JSONObject, key: String): Int? {
        if (!j.has(key) || j.isNull(key)) return null
        return j.optInt(key, -1).takeIf { it in 1..99 }
    }

    /** «الخطوة 2 من 5» كما تُعرض في الفقاعة، أو null إن لم تُذكر. */
    fun stepLabel(step: Int?, total: Int?): String? = when {
        step == null -> null
        total != null && total >= step -> L.f(R.string.step_of, step, total)
        else -> L.f(R.string.step_only, step)
    }
}
