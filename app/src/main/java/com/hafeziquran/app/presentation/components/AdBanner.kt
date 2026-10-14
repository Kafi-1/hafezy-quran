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
 * Adaptive AdMob banner.
 *
 * Only ever placed on screens that are *not* the mushaf reader — showing an ad
 * over the Quran would both break the product promise and violate the app's own
 * "no ads while reading" rule.
 *
 * Uses Google's test unit id in debug builds so development taps are harmless.
 */
private const val TEST_BANNER_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

// TODO(step 9): replace with the real production banner unit id.
private const val PROD_BANNER_UNIT_ID = TEST_BANNER_UNIT_ID

@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val unitId = if (BuildConfig.DEBUG) TEST_BANNER_UNIT_ID else PROD_BANNER_UNIT_ID

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = unitId
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}
