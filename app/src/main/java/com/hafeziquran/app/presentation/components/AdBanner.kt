package com.hafeziquran.app.presentation.components

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.hafeziquran.app.BuildConfig

/**
 * Anchored adaptive AdMob banner.
 *
 * Only ever placed on screens that are *not* the mushaf reader — showing an ad
 * over the Quran would both break the product promise and violate the app's own
 * "no ads while reading" rule.
 *
 * The unit id comes from [BuildConfig.ADMOB_BANNER_UNIT_ID], which the Gradle build
 * resolves to Google's test unit for debug and to the real unit for release.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                // Anchored adaptive: the SDK sizes the banner to the current screen
                // width, which gives a better fill rate than the deprecated fixed
                // AdSize.BANNER constant.
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        context.resources.configuration.screenWidthDp,
                    ),
                )
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { adView ->
            // Tear the ad down with its composition; otherwise the WebView-backed
            // AdView keeps its resources alive after the screen is gone.
            adView.destroy()
        },
    )
}
