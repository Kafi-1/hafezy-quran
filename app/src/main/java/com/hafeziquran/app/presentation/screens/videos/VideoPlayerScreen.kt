@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hafeziquran.app.presentation.screens.videos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hafeziquran.app.BuildConfig
import com.hafeziquran.app.core.util.normalizeYouTubeId

/**
 * Plays a catalogue entry inside the app via YouTube's embed player.
 *
 * A WebView is used rather than an external player because the catalogue only
 * stores a YouTube id: direct stream URLs are not exposed by YouTube.
 *
 * Two load strategies are tried, because WebView behaviour varies across WebView
 * versions and devices:
 *
 *  * [LoadStrategy.IFRAME] wraps the embed in an `<iframe>` inside local HTML
 *    loaded with [REFERER_ORIGIN] as its base URL. The resulting request carries
 *    a genuine `https://com.hafeziquran.app` Referer, which YouTube requires —
 *    without it the embed answers with player Error 153.
 *  * [LoadStrategy.DIRECT] navigates straight to `youtube.com/embed/<id>` and
 *    supplies the same origin as an explicit `Referer` header.
 *
 * If the main frame fails to load, the other strategy is tried automatically; the
 * user can also switch manually, or hand the video to the YouTube app, so a black
 * player never leaves them stuck.
 */
@Composable
fun VideoPlayerScreen(
    youtubeId: String,
    title: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val id = remember(youtubeId) { normalizeYouTubeId(youtubeId) }

    var isFullscreen by remember { mutableStateOf(false) }
    var strategy by remember { mutableIntStateOf(LoadStrategy.DIRECT.ordinal) }
    var failed by remember { mutableStateOf(false) }

    val chromeClient = remember {
        YouTubeChromeClient(context.findActivity()) { fullscreen -> isFullscreen = fullscreen }
    }

    // Back exits fullscreen first, then leaves the screen.
    BackHandler(enabled = isFullscreen) { chromeClient.exitFullscreen() }
    DisposableEffect(Unit) {
        onDispose { chromeClient.exitFullscreen() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { "ভিডিও" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            // Recreating the WebView on a strategy change is deliberate: a
            // half-loaded player cannot be reliably reused.
            key(strategy) {
                YouTubeEmbed(
                    youtubeId = id,
                    strategy = LoadStrategy.entries[strategy],
                    chromeClient = chromeClient,
                    onMainFrameError = {
                        val next = strategy + 1
                        if (next < LoadStrategy.entries.size) {
                            strategy = next
                        } else {
                            failed = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title.ifBlank { "ভিডিও" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (failed) {
                    Text(
                        text = "ভিডিওটি এখানে চালু করা যাচ্ছে না। YouTube অ্যাপে খুলে " +
                            "দেখুন, অথবা আবার চেষ্টা করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "ভিডিও লোড না হলে নিচের বাটনে চাপ দিন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { failed = false; strategy = 0 },
                    ) {
                        Text("আবার চেষ্টা করুন")
                    }
                    TextButton(onClick = { openInYouTube(context, id) }) {
                        Text("YouTube অ্যাপে খুলুন")
                    }
                }
            }
        }
    }
}

// IFRAME first: it is the only strategy that reliably sends the Referer header
// YouTube's embed requires (Error 153 is raised when it is missing).
private enum class LoadStrategy { IFRAME, DIRECT }

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbed(
    youtubeId: String,
    strategy: LoadStrategy,
    chromeClient: WebChromeClient,
    onMainFrameError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                // A software layer on the WebView is a classic cause of a black
                // video surface under Compose, so pin it to hardware.
                setLayerType(View.LAYER_TYPE_HARDWARE, null)

                // Lets chrome://inspect see the embed on debug builds — the only
                // way to read YouTube's own console errors on a device.
                if (BuildConfig.DEBUG) {
                    @Suppress("DEPRECATION")
                    WebView.setWebContentsDebuggingEnabled(true)
                }

                // The embed sets playback cookies on a third-party domain;
                // without them some videos stall on a black frame.
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(false)
                    setSupportZoom(false)
                    builtInZoomControls = false
                    // Remove the "; wv" token: YouTube refuses to play embeds for
                    // a User-Agent that identifies an embedded WebView.
                    userAgentString = userAgentString?.replace("; wv", "")
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url
                        return if (url.scheme == "http" || url.scheme == "https") {
                            false
                        } else {
                            // intent://, market:// etc. belong to the system.
                            runCatching {
                                view.context.startActivity(Intent(Intent.ACTION_VIEW, url))
                            }.onFailure { Log.w(TAG, "No handler for $url", it) }
                            true
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        Log.e(
                            TAG,
                            "error on ${request.url}: ${error.errorCode} ${error.description}",
                        )
                        // Sub-resource failures (ads, thumbnails) are expected;
                        // only a dead main frame means the strategy failed.
                        if (request.isForMainFrame) onMainFrameError()
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        Log.e(TAG, "HTTP ${errorResponse.statusCode} on ${request.url}")
                    }
                }

                webChromeClient = chromeClient

                when (strategy) {
                    LoadStrategy.IFRAME -> loadDataWithBaseURL(
                        REFERER_ORIGIN,
                        embedHtml(youtubeId),
                        "text/html",
                        "utf-8",
                        null,
                    )
                    LoadStrategy.DIRECT -> loadUrl(
                        embedUrl(youtubeId),
                        mapOf("Referer" to "$REFERER_ORIGIN/"),
                    )
                }
            }
        },
        onRelease = { webView ->
            // Stop the player's audio track as soon as the screen is popped.
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}

/** Fullscreen support + console logging so embed failures are debuggable. */
private class YouTubeChromeClient(
    private val activity: Activity?,
    private val onFullscreenChange: (Boolean) -> Unit,
) : WebChromeClient() {

    private var fullscreenView: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null

    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (fullscreenView != null) {
            callback?.onCustomViewHidden()
            return
        }
        val decor = activity?.window?.decorView as? FrameLayout ?: return
        fullscreenView = view
        this.callback = callback
        view?.let {
            decor.addView(it, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        onFullscreenChange(true)
    }

    override fun onHideCustomView() {
        val decor = activity?.window?.decorView as? FrameLayout
        fullscreenView?.let { decor?.removeView(it) }
        fullscreenView = null
        callback?.onCustomViewHidden()
        callback = null
        onFullscreenChange(false)
    }

    fun exitFullscreen() {
        if (fullscreenView != null) onHideCustomView()
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        Log.d(
            TAG,
            "console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})",
        )
        return true
    }

    private companion object {
        const val MATCH_PARENT = FrameLayout.LayoutParams.MATCH_PARENT
    }
}

private fun embedUrl(youtubeId: String): String =
    "$BASE_URL/embed/$youtubeId?autoplay=1&playsinline=1&rel=0&modestbranding=1"

private fun embedHtml(youtubeId: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <style>
        html, body { margin: 0; padding: 0; height: 100%; background: #000; overflow: hidden; }
        iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
      </style>
    </head>
    <body>
      <iframe
        src="${embedUrl(youtubeId)}"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        referrerpolicy="strict-origin-when-cross-origin"
        allowfullscreen>
      </iframe>
    </body>
    </html>
""".trimIndent()

private fun openInYouTube(context: Context, youtubeId: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeId"))
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/watch?v=$youtubeId"),
    )
    runCatching { context.startActivity(appIntent) }
        .onFailure { runCatching { context.startActivity(webIntent) } }
}

/** Walks the ContextWrapper chain to the hosting Activity, for fullscreen views. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val BASE_URL = "https://www.youtube.com"

// Must match the app id in app/build.gradle.kts. YouTube's embed rejects requests
// whose Referer is missing or does not identify a valid origin (player Error 153),
// so every load path supplies this as both base URL and Referer.
private const val REFERER_ORIGIN = "https://com.hafeziquran.app"

private const val TAG = "VideoPlayerScreen"