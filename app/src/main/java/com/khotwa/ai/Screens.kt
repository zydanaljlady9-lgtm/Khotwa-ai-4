package com.khotwa.ai

import android.content.ClipData
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.TextFieldColors
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.derivedStateOf
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** كل ما تحتاجه الشاشات من النظام (أذونات، فتح روابط، دخول). */
class Host(
    val askMic: () -> Unit,
    val dictate: () -> Unit,
    val openAccessibility: () -> Unit,
    val openAppSettings: () -> Unit,
    val openUrl: (String) -> Unit,
    val startGuide: (String) -> Unit,
    val signInGoogle: () -> Unit,
    val continueGuest: () -> Unit,
    val signOut: () -> Unit,
    val pickImage: () -> Unit,
    val setLanguage: (String) -> Unit
)

// ───────────────────────── مكوّنات صغيرة ─────────────────────────

@Composable
private fun RoundButton(
    icon: ImageVector,
    desc: String,
    bg: Color = K.Accent,
    tint: Color = K.OnAccent,
    size: Int = 44,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (bg == K.Accent) K.AccentBrush else SolidColor(bg))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = tint) }
}

@Composable
private fun BarIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = K.Text) }
}

@Composable
private fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) K.AccentBrush else SolidColor(K.SurfaceHigh))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) K.OnAccent else K.Muted,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = K.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(K.Surface)
                .border(1.dp, K.Border, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) K.AccentBrush else SolidColor(K.SurfaceHigh))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(text, color = if (selected) K.OnAccent else K.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Avatar(size: Int) {
    val initial = (Account.name.ifBlank { Account.email }).trim().take(1).uppercase()
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(K.Accent, K.Sky))),
        contentAlignment = Alignment.Center
    ) {
        if (initial.isBlank()) {
            Icon(Icons.Rounded.RadioButtonChecked, null, tint = K.OnAccent)
        } else {
            Text(initial, color = K.OnAccent, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
        }
    }
}

/** نافذة اختيار لغة الواجهة. */
@Composable
fun LanguageDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(L.s(R.string.close), color = K.Accent) } },
        title = { Text(L.s(R.string.choose_language), color = K.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                L.ALL.forEach { lang ->
                    val sel = lang.code == L.code
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) K.Accent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { onPick(lang.code) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang.native, color = K.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (lang.code != "en") Text(lang.english, color = K.Muted, fontSize = 13.sp)
                        if (sel) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.Check, null, tint = K.Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        containerColor = K.Surface
    )
}

private fun copyText(c: Context, text: String) {
    try {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Khotwa", text))
    } catch (_: Throwable) {
    }
}

// ───────────────────────── الترحيب وتسجيل الدخول ─────────────────────────

@Composable
fun WelcomeScreen(host: Host) {
    var pickLang by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(K.WelcomeTop, K.Bg, K.Bg)))) {
    AuroraBackground(Modifier.fillMaxSize())
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, K.Border, RoundedCornerShape(20.dp))
                    .clickable { pickLang = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Language, null, tint = K.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(L.current.native, color = K.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(8.dp))
            KhotwaLogo(140.dp, animated = true)
            Spacer(Modifier.height(8.dp))
            Text(
                L.s(R.string.brand),
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(K.Accent, K.Sky, K.Violet)),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                L.s(R.string.tagline),
                color = K.Muted,
                fontSize = 16.sp,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            GuideDemo()
            Spacer(Modifier.height(18.dp))
            Feature(Icons.Rounded.AutoAwesome, L.s(R.string.feat_ai))
            Feature(Icons.Rounded.Mic, L.s(R.string.feat_voice))
            Feature(Icons.Rounded.RadioButtonChecked, L.s(R.string.feat_circle))
            Feature(Icons.Rounded.Language, L.s(R.string.feat_global))
            Feature(Icons.Rounded.Shield, L.s(R.string.feat_privacy))
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color.White)
                .clickable(onClick = host.signInGoogle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("G", color = Color(0xFF4285F4), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(L.s(R.string.google_continue), color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .border(1.dp, K.Border, RoundedCornerShape(27.dp))
                .clickable(onClick = host.continueGuest),
            contentAlignment = Alignment.Center
        ) { Text(L.s(R.string.guest_continue), color = K.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        Text(
            L.s(R.string.consent_note),
            color = K.Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
    }
    if (pickLang) {
        LanguageDialog(onPick = {
            host.setLanguage(it)
            pickLang = false
        }, onDismiss = { pickLang = false })
    }
}

/** عرض حيّ: دائرة تنتقل إلى الزر الصحيح، وتشرح الفكرة دون كلام. بلا نصوص حتى لا تحتاج ترجمة. */
@Composable
private fun GuideDemo() {
    val t = rememberInfiniteTransition(label = "demo")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing)),
        label = "phase"
    )
    val idx = phase.toInt().coerceIn(0, 2)
    val xs = listOf(20f, 98f, 176f)
    val ys = listOf(22f, 62f, 102f)
    val x by animateFloatAsState(xs[idx], tween(650), label = "dx")
    val y by animateFloatAsState(ys[idx], tween(650), label = "dy")
    val pulse = (phase - phase.toInt())
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(K.Surface)
            .border(1.dp, K.Border, RoundedCornerShape(24.dp))
    ) {
        listOf(Icons.Rounded.Mic, Icons.Rounded.Language, Icons.Rounded.Shield).forEachIndexed { i, ic ->
            Box(
                Modifier
                    .offset(xs[i].dp + 28.dp, ys[i].dp + 14.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(K.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) { Icon(ic, null, tint = K.Muted, modifier = Modifier.size(22.dp)) }
        }
        Box(
            Modifier
                .offset(x.dp + 22.dp, y.dp + 8.dp)
                .size(68.dp)
                .alpha(1f - pulse * 0.8f)
                .clip(CircleShape)
                .border(2.dp, K.Accent, CircleShape)
        )
        Box(
            Modifier
                .offset(x.dp + 36.dp, y.dp + 22.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(K.Accent.copy(alpha = 0.22f))
                .border(2.dp, K.Accent, CircleShape)
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0x1F5EEAD4)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = K.Accent) }
        Spacer(Modifier.width(14.dp))
        Text(text, color = K.Text, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f))
    }
}

// ───────────────────────── الدردشة ─────────────────────────

@Composable
fun ChatScreen(host: Host, onMenu: () -> Unit) {
    val conv = ChatStore.current
    val msgs = conv?.msgs ?: emptyList()
    val thinking = ChatStore.thinking
    val live = ChatStore.streaming
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showJump by remember { derivedStateOf { listState.canScrollForward } }
    val count = msgs.size + if (thinking) 1 else 0
    LaunchedEffect(conv?.id, count, live.length) {
        if (count > 0) {
            listState.scrollToItem(count - 1)
            listState.scrollBy(100000f)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(K.Bg)
            .statusBarsPadding()
            .imePadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(Modifier.align(Alignment.CenterStart)) { BarIcon(Icons.Rounded.Menu, L.s(R.string.menu), onMenu) }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                KhotwaLogo(28.dp)
                Spacer(Modifier.width(8.dp))
                Text(L.s(R.string.brand), color = K.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                BarIcon(Icons.Rounded.Add, L.s(R.string.new_chat)) { ChatStore.newChat() }
            }
        }

        if (!GuideState.micGranted || !GuideState.serviceConnected) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1FFBBF24))
                    .clickable { ChatStore.addSetupCards() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Settings, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    L.s(R.string.setup_incomplete),
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (msgs.isEmpty()) {
                EmptyState(onPick = { ChatStore.send(it) })
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(msgs, key = { it.id }) { m -> MessageRow(m, host, isLast = !thinking && m.id == msgs.last().id) }
                    if (thinking) item(key = "typing") {
                        if (live.isBlank()) TypingRow() else StreamRow(live)
                    }
                    val lastMsg = msgs.lastOrNull()
                    if (!thinking && lastMsg != null && lastMsg.role == "assistant" && lastMsg.actions.isEmpty() && msgs.size > 1) {
                        item(key = "followups") { FollowUps(onPick = { ChatStore.send(it) }) }
                    }
                }
                if (showJump) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(K.Surface)
                            .border(1.dp, K.Border, CircleShape)
                            .clickable {
                                scope.launch {
                                    listState.animateScrollToItem(maxOf(count - 1, 0))
                                    listState.scrollBy(100000f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.ExpandMore, L.s(R.string.scroll_bottom), tint = K.Text) }
                }
            }
        }

        Composer(
            input = input,
            onInput = { input = it },
            busy = thinking,
            onSend = {
                ChatStore.send(input)
                input = ""
            },
            onStop = { ChatStore.stop() },
            onMic = host.dictate,
            onAttach = host.pickImage,
            attached = ChatStore.pendingImage != null,
            onClearAttach = { ChatStore.pendingImage = null }
        )
    }
}

/** تحية حسب وقت اليوم. */
private fun greetingFor(name: String): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val plain: Int
    val named: Int
    when (h) {
        in 5..11 -> { plain = R.string.greet_morning; named = R.string.greet_morning_name }
        in 12..17 -> { plain = R.string.greet_afternoon; named = R.string.greet_afternoon_name }
        else -> { plain = R.string.greet_evening; named = R.string.greet_evening_name }
    }
    return if (name.isNotBlank()) L.f(named, name) else L.s(plain)
}

@Composable
private fun FollowUps(onPick: (String) -> Unit) {
    val items = listOf(R.string.fu_explain, R.string.fu_shorter, R.string.fu_example, R.string.fu_steps).map { L.s(it) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { t ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, K.Border, RoundedCornerShape(18.dp))
                    .clickable { onPick(t) }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) { Text(t, color = K.Text, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun plainFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = K.Text,
    unfocusedTextColor = K.Text,
    cursorColor = K.Accent
)

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    val first = Account.name.trim().substringBefore(' ')
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        KhotwaLogo(110.dp, animated = true)
        Spacer(Modifier.height(10.dp))
        Text(
            greetingFor(first),
            color = K.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(L.s(R.string.empty_sub), color = K.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        val sugIcons = listOf(Icons.Rounded.RadioButtonChecked, Icons.Rounded.AutoAwesome, Icons.Rounded.Language, Icons.Rounded.Bolt)
        listOf(R.string.sug1, R.string.sug2, R.string.sug3, R.string.sug4).map { L.s(it) }.forEachIndexed { i, s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(K.Surface)
                    .border(1.dp, K.Border, RoundedCornerShape(16.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(K.Accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(sugIcons[i % sugIcons.size], null, tint = K.Accent, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(s, color = K.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MessageRow(m: Msg, host: Host, isLast: Boolean = false) {
    val ctx = LocalContext.current
    if (m.role == "user") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                    .background(K.AccentBrush)
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                if (m.hasImage) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Image, null, tint = K.OnAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(L.s(R.string.image_attached), color = K.OnAccent.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(m.text, color = K.OnAccent, fontSize = 16.sp, lineHeight = 24.sp)
            }
        }
    } else {
        var copied by remember(m.id) { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 1.dp)) { KhotwaLogo(30.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer {
                    MarkdownText(m.text)
                }
                m.actions.forEach { ActionCard(it, host) }
                if (m.actions.none { it.type == "retry" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallIcon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, L.s(R.string.copy)) {
                            copyText(ctx, m.text)
                            copied = true
                        }
                        SmallIcon(Icons.AutoMirrored.Rounded.VolumeUp, L.s(R.string.read_aloud)) { Speaker.speak(m.text) }
                        SmallIcon(Icons.Rounded.Share, L.s(R.string.share)) {
                            try {
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, m.text)
                                }
                                ctx.startActivity(Intent.createChooser(i, null))
                            } catch (_: Throwable) {
                            }
                        }
                        if (isLast) SmallIcon(Icons.Rounded.Refresh, L.s(R.string.regenerate)) { ChatStore.regenerate() }
                        if (m.engine.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Bolt, null, tint = K.Muted.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
                            Text(m.engine, color = K.Muted.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = K.Muted, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun StreamRow(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp)) { KhotwaLogo(30.dp, animated = true) }
        Spacer(Modifier.width(10.dp))
        MarkdownText(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TypingRow() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        KhotwaLogo(30.dp, animated = true)
        Spacer(Modifier.width(12.dp))
        for (i in 0..2) {
            val a by t.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i"
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(9.dp)
                    .alpha(a)
                    .clip(CircleShape)
                    .background(K.Accent)
            )
        }
    }
}

private class Spec(val icon: ImageVector, val title: String, val desc: String, val button: String)

@Composable
private fun ActionCard(a: Action, host: Host) {
    val done = when (a.type) {
        "ask_mic" -> GuideState.micGranted
        "ask_accessibility" -> GuideState.serviceConnected
        else -> false
    }
    var used by remember(a) { mutableStateOf(false) }
    val spec = when (a.type) {
        "ask_mic" -> Spec(Icons.Rounded.Mic, L.s(R.string.card_mic_title), L.s(R.string.card_mic_desc), L.s(R.string.allow))
        "ask_accessibility" -> Spec(
            Icons.Rounded.Accessibility, L.s(R.string.card_acc_title), L.s(R.string.card_acc_desc), L.s(R.string.card_acc_btn)
        )
        "retry" -> Spec(Icons.Rounded.Refresh, L.s(R.string.retry), L.s(R.string.card_retry_desc), L.s(R.string.retry))
        "open_url" -> Spec(
            Icons.Rounded.OpenInNew, a.label.ifBlank { L.s(R.string.card_url_title) },
            a.url.removePrefix("https://").take(60), if (used) L.s(R.string.card_url_again) else L.s(R.string.card_url_title)
        )
        else -> Spec(
            Icons.Rounded.PlayArrow, a.label.ifBlank { L.s(R.string.card_guide_title) },
            L.s(R.string.card_guide_desc), if (used) L.s(R.string.card_guide_again) else L.s(R.string.card_guide_btn)
        )
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(K.Surface)
            .border(1.dp, if (done) K.Ok.copy(alpha = 0.55f) else K.Border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (done) K.Ok.copy(alpha = 0.18f) else K.Accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (done) Icons.Rounded.Check else spec.icon, null, tint = if (done) K.Ok else K.Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(spec.title, color = K.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    spec.desc,
                    color = K.Muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (done) {
            Text(L.s(R.string.done_check), color = K.Ok, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } else {
            AccentButton(spec.button) {
                used = true
                when (a.type) {
                    "ask_mic" -> host.askMic()
                    "ask_accessibility" -> host.openAccessibility()
                    "retry" -> ChatStore.retry()
                    "open_url" -> host.openUrl(a.url)
                    else -> host.startGuide(a.goal)
                }
            }
            if (a.type == "ask_accessibility") {
                TextButton(onClick = host.openAppSettings) {
                    Text(L.s(R.string.card_acc_hint), color = K.Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onAttach: () -> Unit,
    attached: Boolean,
    onClearAttach: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    if (attached) {
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(K.SurfaceHigh)
                .clickable(onClick = onClearAttach)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Image, null, tint = K.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(L.s(R.string.attached_hint), color = K.Text, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Close, L.s(R.string.remove), tint = K.Muted, modifier = Modifier.size(16.dp))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape)
            .background(K.SurfaceHigh)
            .border(1.dp, Brush.linearGradient(listOf(K.Accent.copy(alpha = 0.55f), K.Violet.copy(alpha = 0.35f))), shape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onAttach),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Image, L.s(R.string.attach_image), tint = K.Muted) }
        TextField(
            value = input,
            onValueChange = onInput,
            placeholder = { Text(L.s(R.string.input_hint), color = K.Muted, fontSize = 15.sp) },
            maxLines = 5,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = K.Text,
                unfocusedTextColor = K.Text,
                cursorColor = K.Accent
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        if (busy) {
            RoundButton(Icons.Rounded.Stop, L.s(R.string.stop), bg = K.Text, tint = K.Bg, onClick = onStop)
        } else if (input.isBlank() && !attached) {
            RoundButton(Icons.Rounded.Mic, L.s(R.string.speak), onClick = onMic)
        } else {
            val haptic = LocalHapticFeedback.current
            RoundButton(Icons.Rounded.ArrowUpward, L.s(R.string.send), onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSend()
            })
        }
    }
}

// ───────────────────────── القائمة الجانبية ─────────────────────────

@Composable
fun DrawerContent(onClose: () -> Unit, onSettings: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = K.Surface, drawerContentColor = K.Text) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                KhotwaLogo(34.dp)
                Spacer(Modifier.width(10.dp))
                Text(L.s(R.string.brand), color = K.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(K.AccentBrush)
                    .clickable {
                        ChatStore.newChat()
                        onClose()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Add, null, tint = K.OnAccent)
                Spacer(Modifier.width(10.dp))
                Text(L.s(R.string.new_chat), color = K.OnAccent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            var query by remember { mutableStateOf("") }
            var renaming by remember { mutableStateOf<Conv?>(null) }
            var newTitle by remember { mutableStateOf("") }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(K.SurfaceHigh)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = K.Muted, modifier = Modifier.size(18.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    placeholder = { Text(L.s(R.string.search_chats), color = K.Muted, fontSize = 14.sp) },
                    singleLine = true,
                    colors = plainFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(L.s(R.string.chats), color = K.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
            Spacer(Modifier.height(4.dp))
            val shown = ChatStore.search(query)
            LazyColumn(Modifier.weight(1f)) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) L.s(R.string.no_chats) else L.s(R.string.no_results),
                            color = K.Muted, fontSize = 13.sp, modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                items(shown, key = { it.id }) { c ->
                    val sel = c.id == ChatStore.currentId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) K.SurfaceHigh else Color.Transparent)
                            .clickable {
                                ChatStore.open(c.id)
                                onClose()
                            }
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            c.title,
                            color = K.Text,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    newTitle = c.title
                                    renaming = c
                                },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Edit, L.s(R.string.rename), tint = K.Muted, modifier = Modifier.size(18.dp)) }
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { ChatStore.delete(c.id) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Delete, L.s(R.string.delete), tint = K.Muted, modifier = Modifier.size(19.dp)) }
                    }
                }
            }
            renaming?.let { target ->
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    containerColor = K.Surface,
                    title = { Text(L.s(R.string.rename), color = K.Text) },
                    text = {
                        TextField(
                            value = newTitle,
                            onValueChange = { newTitle = it.take(60) },
                            singleLine = true,
                            colors = plainFieldColors(),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(K.SurfaceHigh)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            ChatStore.rename(target.id, newTitle)
                            renaming = null
                        }) { Text(L.s(R.string.ok), color = K.Accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { renaming = null }) { Text(L.s(R.string.cancel), color = K.Muted) }
                    }
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onClose()
                        onSettings()
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Account.name.ifBlank { L.s(R.string.guest) },
                        color = K.Text,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (Account.signedIn) Account.email else L.s(R.string.settings),
                        color = K.Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Rounded.Settings, null, tint = K.Muted)
            }
        }
    }
}

// ───────────────────────── الإعدادات ─────────────────────────

@Composable
fun SettingsScreen(host: Host, onBack: () -> Unit, onSetVoice: (String) -> Unit, onSetSpeak: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf(Prefs.apiKey(ctx)) }
    var keyMsg by remember { mutableStateOf("") }
    var keyOk by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(GuideState.hasKey) }
    var showReader by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickLang by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(K.Bg)
            .statusBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(Modifier.align(Alignment.CenterStart)) {
                BarIcon(Icons.AutoMirrored.Rounded.ArrowBack, L.s(R.string.back), onBack)
            }
            Text(
                L.s(R.string.settings),
                color = K.Text,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Section(L.s(R.string.account)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(52)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Account.name.ifBlank { L.s(R.string.guest) }, color = K.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (Account.signedIn) Account.email else L.s(R.string.guest_desc),
                            color = K.Muted,
                            fontSize = 13.sp
                        )
                    }
                }
                if (Account.signedIn) {
                    AccentButton(L.s(R.string.sign_out), onClick = host.signOut)
                } else {
                    AccentButton(L.s(R.string.link_google), onClick = host.signInGoogle)
                }
            }

            Section(L.s(R.string.lang_section)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(K.SurfaceHigh)
                        .clickable { pickLang = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Language, null, tint = K.Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(L.s(R.string.app_language), color = K.Muted, fontSize = 12.sp)
                        Text(L.current.native, color = K.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Rounded.ExpandMore, null, tint = K.Muted)
                }
                Text(L.s(R.string.voice_language), color = K.Text, fontSize = 15.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    L.current.voices.forEach { (tag, name) ->
                        Chip(name, GuideState.language == tag) { onSetVoice(tag) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L.s(R.string.speak_replies), color = K.Text, fontSize = 15.sp)
                        Text(L.s(R.string.speak_replies_sub), color = K.Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = GuideState.speakReplies,
                        onCheckedChange = onSetSpeak,
                        colors = SwitchDefaults.colors(checkedThumbColor = K.OnAccent, checkedTrackColor = K.Accent)
                    )
                }
            }

            Section(L.s(R.string.section_appearance)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(L.s(R.string.theme_system), GuideState.themeMode == "system") { Prefs.setTheme(ctx, "system") }
                    Chip(L.s(R.string.theme_dark), GuideState.themeMode == "dark") { Prefs.setTheme(ctx, "dark") }
                    Chip(L.s(R.string.theme_light), GuideState.themeMode == "light") { Prefs.setTheme(ctx, "light") }
                }
            }

            Section(L.s(R.string.section_answers)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(L.s(R.string.style_short), GuideState.answerStyle == "short") { Prefs.setAnswerStyle(ctx, "short") }
                    Chip(L.s(R.string.style_balanced), GuideState.answerStyle == "balanced") { Prefs.setAnswerStyle(ctx, "balanced") }
                    Chip(L.s(R.string.style_detailed), GuideState.answerStyle == "detailed") { Prefs.setAnswerStyle(ctx, "detailed") }
                }
                Text(L.s(R.string.custom_title), color = K.Text, fontSize = 15.sp)
                var custom by remember { mutableStateOf(GuideState.customInstructions) }
                TextField(
                    value = custom,
                    onValueChange = {
                        custom = it.take(600)
                        Prefs.setCustomInstructions(ctx, custom)
                    },
                    placeholder = { Text(L.s(R.string.custom_hint), color = K.Muted, fontSize = 14.sp) },
                    minLines = 3,
                    maxLines = 6,
                    colors = plainFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(K.SurfaceHigh)
                )
            }

            Section(L.s(R.string.permissions)) {
                PermRow(L.s(R.string.perm_mic), GuideState.micGranted, L.s(R.string.allow), host.askMic)
                PermRow(L.s(R.string.perm_acc), GuideState.serviceConnected, L.s(R.string.enable), host.openAccessibility)
            }

            Section(L.s(R.string.ai_section)) {
                EngineRow(
                    Icons.Rounded.AutoAwesome,
                    L.s(R.string.ai_builtin_title),
                    L.s(R.string.ai_builtin_desc),
                    L.s(R.string.ai_ready),
                    true
                )
                OnDeviceRow()
                Text(
                    if (GuideState.engine.isBlank()) L.s(R.string.engine_none) else L.f(R.string.engine_last, GuideState.engine),
                    color = K.Muted,
                    fontSize = 12.sp
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showKey = !showKey }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(L.s(R.string.key_title), color = K.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(if (showKey) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = K.Accent)
                }
                AnimatedVisibility(showKey) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(L.s(R.string.key_desc), color = K.Muted, fontSize = 13.sp, lineHeight = 20.sp)
                        TextField(
                            value = keyInput,
                            onValueChange = {
                                keyInput = it
                                keyMsg = ""
                            },
                            placeholder = { Text("AQ.… / sk-…", color = K.Muted) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = K.SurfaceHigh,
                                unfocusedContainerColor = K.SurfaceHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = K.Text,
                                unfocusedTextColor = K.Text,
                                cursorColor = K.Accent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        AccentButton(if (testing) L.s(R.string.key_testing) else L.s(R.string.key_save), enabled = !testing) {
                            val k = keyInput.trim()
                            if (k.isEmpty()) {
                                keyOk = false
                                keyMsg = L.s(R.string.key_paste_first)
                            } else {
                                testing = true
                                keyMsg = ""
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { AiClient.test(k) }
                                    testing = false
                                    r.fold(
                                        { name ->
                                            Prefs.setApiKey(ctx, k)
                                            keyOk = true
                                            keyMsg = L.f(R.string.key_ok, name)
                                        },
                                        { e ->
                                            keyOk = false
                                            val m = e.message.orEmpty()
                                            keyMsg = when {
                                                e is IllegalArgumentException -> L.s(R.string.key_unknown_type)
                                                m.contains("401") -> L.s(R.string.key_bad)
                                                m.contains("429") -> L.s(R.string.key_quota)
                                                m.contains("403") -> L.s(R.string.key_denied)
                                                else -> L.f(R.string.key_failed, m.ifBlank { e.javaClass.simpleName })
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        if (keyMsg.isNotEmpty()) {
                            Text(keyMsg, color = if (keyOk) K.Ok else K.Danger, fontSize = 13.sp)
                        }
                        if (GuideState.hasKey) {
                            TextButton(onClick = {
                                Prefs.setApiKey(ctx, "")
                                keyInput = ""
                                keyOk = true
                                keyMsg = L.s(R.string.key_removed)
                            }) { Text(L.s(R.string.key_remove), color = K.Danger) }
                        }
                    }
                }
            }

            Section(L.s(R.string.vision_section)) {
                val canSee = AiClient.hasVision()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L.s(R.string.vision_title), color = K.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(L.s(R.string.vision_desc), color = K.Muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = GuideState.vision && canSee,
                        enabled = canSee,
                        onCheckedChange = { Prefs.setVision(ctx, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = K.OnAccent, checkedTrackColor = K.Accent)
                    )
                }
                Text(L.s(R.string.vision_note), color = K.Muted, fontSize = 12.sp, lineHeight = 18.sp)
                if (!canSee) Text(L.s(R.string.vision_needs), color = K.Danger, fontSize = 12.sp, lineHeight = 18.sp)
            }

            Section(L.s(R.string.privacy_section)) {
                listOf(
                    R.string.priv1, R.string.priv2, R.string.priv3, R.string.priv4,
                    R.string.priv5, R.string.priv6, R.string.priv7
                ).forEach { PrivacyLine(L.s(it)) }
            }

            Section(L.s(R.string.advanced)) {
                TextButton(onClick = { showReader = !showReader }) {
                    Text(if (showReader) L.s(R.string.reader_hide) else L.s(R.string.reader_show), color = K.Accent)
                }
                if (showReader) {
                    Text(L.s(R.string.reader_hint), color = K.Muted, fontSize = 13.sp)
                    AccentButton(L.s(R.string.reader_start)) {
                        KhotwaAccessibilityService.instance?.readScreenDelayed(6000)
                    }
                    GuideState.screenLines.forEach { Text(it, color = K.Text, fontSize = 13.sp) }
                    if (GuideState.screenLines.isEmpty() && GuideState.lastResult.isNotEmpty()) {
                        Text(GuideState.lastResult, color = K.Danger, fontSize = 13.sp)
                    }
                }
                if (confirmDelete) {
                    Text(L.s(R.string.delete_confirm), color = K.Text, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = {
                            ChatStore.clearAll()
                            confirmDelete = false
                        }) { Text(L.s(R.string.yes_delete), color = K.Danger) }
                        TextButton(onClick = { confirmDelete = false }) { Text(L.s(R.string.cancel), color = K.Muted) }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(L.s(R.string.delete_all), color = K.Danger)
                    }
                }
            }

            Text(
                L.f(R.string.version, BuildConfig.VERSION_NAME),
                color = K.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
    }
    if (pickLang) {
        LanguageDialog(onPick = {
            host.setLanguage(it)
            pickLang = false
        }, onDismiss = { pickLang = false })
    }
}

@Composable
private fun EngineRow(icon: ImageVector, title: String, desc: String, badge: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(K.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = K.Accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = K.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background((if (ok) K.Ok else K.Muted).copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) { Text(badge, color = if (ok) K.Ok else K.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Text(desc, color = K.Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OnDeviceRow() {
    LaunchedEffect(Unit) { OnDeviceAi.refresh() }
    val st = OnDeviceAi.state
    val (badge, ok) = when (st) {
        OnDeviceAi.State.READY -> L.s(R.string.ai_ready) to true
        OnDeviceAi.State.DOWNLOADING -> L.s(R.string.ondevice_downloading) to false
        OnDeviceAi.State.DOWNLOADABLE -> L.s(R.string.ondevice_downloadable) to false
        OnDeviceAi.State.CHECKING -> "…" to false
        OnDeviceAi.State.UNAVAILABLE -> L.s(R.string.ondevice_unavailable) to false
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EngineRow(Icons.Rounded.Memory, L.s(R.string.ondevice_title), L.s(R.string.ondevice_desc), badge, ok)
        when (st) {
            OnDeviceAi.State.DOWNLOADABLE -> AccentButton(L.s(R.string.ondevice_download)) { OnDeviceAi.download() }
            OnDeviceAi.State.DOWNLOADING -> {
                LinearProgressIndicator(color = K.Accent, trackColor = K.SurfaceHigh, modifier = Modifier.fillMaxWidth())
                if (OnDeviceAi.downloadedMb > 0) Text(L.f(R.string.ondevice_progress, OnDeviceAi.downloadedMb), color = K.Muted, fontSize = 12.sp)
            }
            else -> {}
        }
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.Shield, null, tint = K.Accent,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(15.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = K.Muted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PermRow(title: String, granted: Boolean, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (granted) Icons.Rounded.Check else Icons.Rounded.Shield,
            null,
            tint = if (granted) K.Ok else K.Muted
        )
        Spacer(Modifier.width(12.dp))
        Text(title, color = K.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (granted) {
            Text(L.s(R.string.enabled), color = K.Ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(K.Accent)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(button, color = K.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
