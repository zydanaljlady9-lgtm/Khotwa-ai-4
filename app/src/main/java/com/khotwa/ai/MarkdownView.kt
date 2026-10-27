package com.khotwa.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection

private fun mdCopy(c: Context, text: String) {
    try {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Khotwa", text))
    } catch (_: Throwable) {
    }
}

private fun styled(spans: List<MdSpan>, accent: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        val start = length
        append(s.text)
        val end = length
        if (s.bold) addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
        if (s.italic) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
        if (s.code) addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp), start, end)
        val url = s.link
        if (url != null) {
            addStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline), start, end)
            addStringAnnotation("URL", url, start, end)
        }
    }
}

@Composable
private fun MdParagraph(spans: List<MdSpan>, size: TextUnit, weight: FontWeight? = null, color: Color = K.Text) {
    val text = styled(spans, K.Accent, K.CodeBg)
    val style = TextStyle(color = color, fontSize = size, lineHeight = size * 1.6f, fontWeight = weight)
    val hasLink = text.getStringAnnotations("URL", 0, text.length).isNotEmpty()
    if (hasLink) {
        val uri = LocalUriHandler.current
        ClickableText(text, style = style, onClick = { off ->
            text.getStringAnnotations("URL", off, off).firstOrNull()?.let {
                try {
                    uri.openUri(it.item)
                } catch (_: Throwable) {
                }
            }
        })
    } else {
        Text(text, style = style)
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val ctx = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    // الكود يُكتب من اليسار لليمين دائماً حتى داخل الواجهة العربية.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(K.CodeBg)
                .border(1.dp, K.Border, shape)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(K.SurfaceHigh)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lang.ifBlank { "code" }, color = K.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            mdCopy(ctx, code)
                            copied = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        L.s(R.string.copy_code),
                        tint = if (copied) K.Ok else K.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    code,
                    color = K.Text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

/** يعرض رد المساعد منسّقاً: عناوين، قوائم، خط عريض ومائل، كود، اقتباسات وروابط. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, size: TextUnit = 16.sp) {
    val blocks = remember(text) { Markdown.parse(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Heading -> MdParagraph(
                    b.spans,
                    when (b.level) {
                        1 -> 22.sp
                        2 -> 20.sp
                        3 -> 18.sp
                        else -> size
                    },
                    FontWeight.Bold
                )
                is MdBlock.Para -> MdParagraph(b.spans, size)
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEach { item ->
                        Row {
                            Text("•", color = K.Accent, fontSize = size, modifier = Modifier.width(18.dp))
                            Box(Modifier.weight(1f)) { MdParagraph(item, size) }
                        }
                    }
                }
                is MdBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEachIndexed { i, item ->
                        Row {
                            Text("${b.start + i}.", color = K.Accent, fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                            Box(Modifier.weight(1f)) { MdParagraph(item, size) }
                        }
                    }
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(K.Accent)
                    )
                    Spacer(Modifier.width(10.dp))
                    MdParagraph(b.spans, size, color = K.Muted)
                }
                is MdBlock.Code -> CodeBlock(b.lang, b.code)
                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(K.Border)
                )
            }
        }
    }
}
