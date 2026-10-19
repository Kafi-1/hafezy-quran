package com.hafeziquran.app.presentation.screens.videos

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Plays a catalogue entry inside the app via YouTube's privacy-enhanced embed.
 *
 * A WebView iframe is used rather than an external player because the catalogue
 * only stores a YouTube id: direct stream URLs are not exposed by YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    youtubeId: String,
    title: String,
    onBack: () -> Unit,
) {
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
            YouTubeEmbed(
                youtubeId = youtubeId,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title.ifBlank { "ভিডিও" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "ভিডিওটি ইন্টারনেট থেকে সরাসরি চলছে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbed(youtubeId: String, modifier: Modifier = Modifier) {
    val html = remember(youtubeId) {
        """
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
            src="https://www.youtube-nocookie.com/embed/$youtubeId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowfullscreen>
          </iframe>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadDataWithBaseURL(
                    "https://www.youtube.com",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        onRelease = { webView ->
            // Stop the iframe's audio track as soon as the screen is popped.
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}
