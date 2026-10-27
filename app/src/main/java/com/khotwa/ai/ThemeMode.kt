package com.khotwa.ai

/** system | dark | light → هل نستخدم الثيم الداكن؟ بلا اعتماد على أندرويد فتُختبر وحدته. */
fun resolveDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
    "dark" -> true
    "light" -> false
    else -> systemDark
}
