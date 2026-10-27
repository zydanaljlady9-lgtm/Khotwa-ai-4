package com.khotwa.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.hasMicPermission(): Boolean =
    checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        Account.load(this)
        ChatStore.load(this)
        Speaker.init(this)
        OnDeviceAi.refresh()
        GuideState.micGranted = hasMicPermission()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (L.current.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                KhotwaTheme { Root(this) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GuideState.appForeground = true
        GuideState.micGranted = hasMicPermission()
        KhotwaAccessibilityService.instance?.setButtonHidden(true)
    }

    override fun onPause() {
        super.onPause()
        GuideState.appForeground = false
        KhotwaAccessibilityService.instance?.setButtonHidden(false)
    }
}

@Composable
private fun Root(activity: Activity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<String?>(null) }
    var micDenied by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val permsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val ok = res[Manifest.permission.RECORD_AUDIO] == true || ctx.hasMicPermission()
        GuideState.micGranted = ok
        if (!ok) micDenied = true
    }
    val dictateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) ChatStore.send(t)
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = withContext(Dispatchers.IO) { ImageUtil.encode(ctx, uri) }
                if (b64 != null) ChatStore.pendingImage = b64 else info = L.s(R.string.err_image_read)
            }
        }
    }

    fun appDetails() {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        )
    }

    val host = remember {
        Host(
            askMic = {
                permsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            },
            dictate = {
                try {
                    dictateLauncher.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, GuideState.language)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, L.s(R.string.speak_now))
                        }
                    )
                } catch (_: Exception) {
                    info = L.s(R.string.err_no_recognizer)
                }
            },
            openAccessibility = {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            openAppSettings = { appDetails() },
            openUrl = { url ->
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    info = L.s(R.string.err_open_url)
                }
            },
            startGuide = { goal ->
                val svc = KhotwaAccessibilityService.instance
                if (svc == null) {
                    info = L.s(R.string.err_need_acc)
                } else if (!GuideState.micGranted) {
                    info = L.s(R.string.err_need_mic)
                } else {
                    svc.startGuide(goal)
                    activity.moveTaskToBack(true)
                }
            },
            signInGoogle = {
                if (!GoogleAuth.configured) {
                    info = L.s(R.string.err_google_not_configured)
                } else {
                    scope.launch {
                        GoogleAuth.signIn(activity).fold(
                            { (name, email) ->
                                Account.setGoogle(ctx, name, email)
                                ChatStore.startWelcome()
                            },
                            { e ->
                                when (e) {
                                    is GoogleAuth.Cancelled -> {}
                                    is GoogleAuth.NoAccount ->
                                        info = L.s(R.string.err_no_google_account)
                                    else ->
                                        info = L.f(R.string.err_sign_in, e.javaClass.simpleName)
                                }
                            }
                        )
                    }
                }
            },
            continueGuest = {
                Account.setGuest(ctx)
                ChatStore.startWelcome()
            },
            pickImage = {
                if (!AiClient.hasVision()) {
                    info = L.s(R.string.err_vision_needs)
                } else {
                    pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            setLanguage = { code -> L.set(ctx, code) },
            signOut = {
                scope.launch {
                    GoogleAuth.signOut(activity)
                    ChatStore.clearAll()
                    Account.signOut(ctx)
                    showSettings = false
                }
            }
        )
    }

    if (!Account.onboarded) {
        WelcomeScreen(host)
    } else if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            host = host,
            onBack = { showSettings = false },
            onSetVoice = { Prefs.setLanguage(ctx, it) },
            onSetSpeak = { Prefs.setSpeak(ctx, it) }
        )
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        BackHandler(enabled = drawerState.currentValue == DrawerValue.Open) {
            scope.launch { drawerState.close() }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(
                    onClose = { scope.launch { drawerState.close() } },
                    onSettings = { showSettings = true }
                )
            }
        ) {
            ChatScreen(host, onMenu = { scope.launch { drawerState.open() } })
        }
    }

    info?.let { msg ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton(onClick = { info = null }) { Text(L.s(R.string.ok), color = K.Accent) } },
            title = { Text(L.s(R.string.brand), color = K.Text) },
            text = { Text(msg, color = K.Muted) },
            containerColor = K.Surface
        )
    }
    if (micDenied) {
        AlertDialog(
            onDismissRequest = { micDenied = false },
            confirmButton = {
                TextButton(onClick = {
                    micDenied = false
                    appDetails()
                }) { Text(L.s(R.string.open_app_settings), color = K.Accent) }
            },
            dismissButton = { TextButton(onClick = { micDenied = false }) { Text(L.s(R.string.later), color = K.Muted) } },
            title = { Text(L.s(R.string.mic_denied_title), color = K.Text) },
            text = {
                Text(
                    L.s(R.string.mic_denied_body),
                    color = K.Muted
                )
            },
            containerColor = K.Surface
        )
    }
}
