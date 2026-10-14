package com.hafeziquran.app.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.hafeziquran.app.BuildConfig

/**
 * One place for every "leave the app" action (store, share sheet, browser,
 * mail). Keeping the URIs here means Settings and Home can't drift apart, and a
 * missing Play Store / mail client degrades to a no-op instead of a crash.
 */
object AppLinks {

    private const val PLAY_STORE_BASE = "https://play.google.com/store/apps/details?id="

    /** Replace with the real support address before publishing. */
    const val DEVELOPER_EMAIL = "support@example.com"
    const val WEBSITE_URL = "https://example.com"

    fun rateApp(context: Context) {
        // `market://` opens the Play Store app; if it is absent, fall back to the
        // web listing, and if that fails the launch is simply dropped.
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}"))
        launchOrWeb(context, marketIntent, PLAY_STORE_BASE + BuildConfig.APPLICATION_ID)
    }

    fun shareApp(context: Context) {
        val text = "হাফেজী কোরআন শরীফ অ্যাপটি ব্যবহার করে দেখুন: " +
            PLAY_STORE_BASE + BuildConfig.APPLICATION_ID
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "শেয়ার করুন"))
    }

    fun openWebsite(context: Context) {
        openUrl(context, WEBSITE_URL)
    }

    fun contactDeveloper(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_EMAIL"))
        runCatching { context.startActivity(intent) }
    }

    fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun launchOrWeb(context: Context, primary: Intent, webFallback: String) {
        try {
            context.startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            openUrl(context, webFallback)
        }
    }
}
