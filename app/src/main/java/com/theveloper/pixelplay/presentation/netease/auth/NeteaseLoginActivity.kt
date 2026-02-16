package com.theveloper.pixelplay.presentation.netease.auth

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

/**
 * WebView-based login for Netease Cloud Music.
 * User logs in at music.163.com in the WebView, then taps "Done" to
 * capture the MUSIC_U session cookie. Cookies are saved directly via
 * the ViewModel/Repository — no activity result needed.
 *
 * Same approach as NeriPlayer.
 */
@AndroidEntryPoint
class NeteaseLoginActivity : ComponentActivity() {

    companion object {
        const val TARGET_URL = "https://music.163.com/"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelPlayTheme {
                NeteaseWebLoginScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteaseWebLoginScreen(
    viewModel: NeteaseLoginViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    val loginState by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle login state changes
    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is NeteaseLoginState.Success -> {
                Toast.makeText(context, "Welcome, ${state.nickname}!", Toast.LENGTH_SHORT).show()
                onClose()
            }
            is NeteaseLoginState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Login to Netease",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Refresh button
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                    // Done button: read cookies and process
                    FilledTonalButton(
                        onClick = {
                            readAndProcessCookies(
                                onSuccess = { cookieJson -> viewModel.processCookies(cookieJson) },
                                onError = { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = loginState !is NeteaseLoginState.Loading,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        if (loginState is NeteaseLoginState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (loginState is NeteaseLoginState.Loading) "Saving…" else "Done",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        NeteaseWebView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            onWebViewCreated = { webView = it }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NeteaseWebView(
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = NeteaseLoginActivity.DESKTOP_UA
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadUrl(NeteaseLoginActivity.TARGET_URL)
                onWebViewCreated(this)
            }
        }
    )
}

/**
 * Read cookies from the WebView's CookieManager and pass them as JSON.
 */
private fun readAndProcessCookies(
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val cm = CookieManager.getInstance()
        val main = cm.getCookie("https://music.163.com") ?: ""
        val api = cm.getCookie("https://interface.music.163.com") ?: ""
        val merged = listOf(main, api).filter { it.isNotBlank() }.joinToString("; ")

        if (merged.isBlank()) {
            onError("No cookies found. Please log in first.")
            return
        }

        val map = cookieStringToMap(merged)
        if (!map.containsKey("os")) map["os"] = "pc"
        if (!map.containsKey("appver")) map["appver"] = "8.10.35"

        if (!map.containsKey("MUSIC_U")) {
            onError("Login not detected yet. Complete login and try again.")
            return
        }

        val json = JSONObject(map as Map<*, *>).toString()
        onSuccess(json)
    } catch (e: Throwable) {
        onError("Failed: ${e.message}")
    }
}

private fun cookieStringToMap(raw: String): MutableMap<String, String> {
    val map = linkedMapOf<String, String>()
    raw.split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains('=') }
        .forEach { part ->
            val idx = part.indexOf('=')
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    return map
}
