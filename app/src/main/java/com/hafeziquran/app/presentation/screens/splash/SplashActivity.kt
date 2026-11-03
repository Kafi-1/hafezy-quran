package com.hafeziquran.app.presentation.screens.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.hafeziquran.app.MainActivity
import com.hafeziquran.app.R

/**
 * First-launch welcome: a calm 2.3 s gesture where a cream field settles in and
 * the emerald "Hafezy Quran" wordmark resolves out of a blur while a soft gold
 * highlight sweeps across it.
 *
 * Plain Android views + [Animator]s only — no Compose, Lottie or bitmaps — so the
 * frame budget stays empty and the launch reads as instant on the eye.
 *
 * Shown exactly once after a fresh install: [hasShown] is persisted the moment the
 * timeline starts, and any Activity recreation (a rotation mid-animation) skips
 * straight to Home. Every later launch therefore goes from cold start to the nav
 * graph with nothing in between.
 */
class SplashActivity : Activity() {

    private var timeline: AnimatorSet? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recreated mid-animation, or simply not the first run: hand off at once.
        if (savedInstanceState != null || hasShown()) {
            goHome()
            return
        }

        setContentView(R.layout.activity_splash)
        markShown()

        val root = findViewById<FrameLayout>(R.id.splash_root)
        val title = findViewById<TextView>(R.id.splash_title)

        root.alpha = 0f
        title.alpha = 0f
        title.scaleX = TEXT_START_SCALE
        title.scaleY = TEXT_START_SCALE

        // The gold sweep sizes its gradient from the measured wordmark, so the
        // whole timeline is armed on the first layout pass.
        title.doOnLayout { startTimeline(root, title) }
    }

    private fun startTimeline(root: View, title: TextView) {
        val emerald = getColor(R.color.brand_green)
        val gold = getColor(R.color.brand_gold)

        val backgroundFade = ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f).apply {
            duration = BACKGROUND_MS
            interpolator = DecelerateInterpolator()
        }

        val textFade = ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f).apply {
            duration = TEXT_MS
            startDelay = TEXT_DELAY
            interpolator = DecelerateInterpolator()
        }

        val textScaleX = ObjectAnimator.ofFloat(title, View.SCALE_X, TEXT_START_SCALE, 1f).apply {
            duration = TEXT_MS
            startDelay = TEXT_DELAY
            interpolator = DecelerateInterpolator()
        }

        val textScaleY = ObjectAnimator.ofFloat(title, View.SCALE_Y, TEXT_START_SCALE, 1f).apply {
            duration = TEXT_MS
            startDelay = TEXT_DELAY
            interpolator = DecelerateInterpolator()
        }

        val sweep = goldSweep(title, emerald, gold)
        val blur = blurToSharp(title)
        val exitFade = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).apply {
            duration = EXIT_MS
            startDelay = EXIT_DELAY
            interpolator = AccelerateInterpolator()
        }

        val children = mutableListOf<Animator>(
            backgroundFade, textFade, textScaleX, textScaleY, sweep, exitFade,
        )
        blur?.let(children::add)

        timeline = AnimatorSet().apply {
            playTogether(children)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!isFinishing && !isDestroyed) goHome()
                }
            })
            start()
        }
    }

    /**
     * Paints a soft gold band onto the wordmark and slides it left → right by
     * translating the gradient's local matrix. The band sits off the left edge at
     * t = -w and off the right edge at t = +w, so before and after the sweep the
     * text is pure emerald; the shader is dropped once it finishes.
     */
    private fun goldSweep(title: TextView, emerald: Int, gold: Int): Animator {
        val half = title.width.toFloat().coerceAtLeast(1f)
        val matrix = Matrix().apply { setTranslate(-half, 0f) }
        val shader = LinearGradient(
            -half, 0f, half, 0f,
            intArrayOf(emerald, emerald, gold, GOLD_HIGHLIGHT, gold, emerald, emerald),
            floatArrayOf(0f, 0.34f, 0.44f, 0.5f, 0.56f, 0.66f, 1f),
            Shader.TileMode.CLAMP,
        ).apply { setLocalMatrix(matrix) }
        title.paint.shader = shader

        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWEEP_MS
            startDelay = SWEEP_DELAY
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                matrix.setTranslate(-half + 2f * half * fraction, 0f)
                shader.setLocalMatrix(matrix)
                title.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    title.paint.shader = null
                    title.invalidate()
                }
            })
        }
    }

    /**
     * A true soften-to-sharp resolve via [RenderEffect] blur, available from
     * Android 12. Older releases simply fade the wordmark in with the scale.
     */
    private fun blurToSharp(title: TextView): Animator? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return ValueAnimator.ofFloat(BLUR_START_RADIUS, 0f).apply {
            duration = TEXT_MS
            startDelay = TEXT_DELAY
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val radius = animator.animatedValue as Float
                title.setRenderEffect(
                    if (radius <= 0.1f) {
                        null
                    } else {
                        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                    },
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        // Drop the timeline so its listeners can never outlive the Activity.
        timeline?.cancel()
        timeline = null
        super.onDestroy()
    }

    private fun hasShown(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SHOWN, false)

    private fun markShown() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, true).apply()
    }

    private companion object {
        const val PREFS = "splash_prefs"
        const val KEY_SHOWN = "welcome_shown"

        // Timeline, milliseconds — see the class KDoc for the phases.
        const val BACKGROUND_MS = 400L
        const val TEXT_DELAY = 400L
        const val TEXT_MS = 800L
        const val SWEEP_DELAY = 1200L
        const val SWEEP_MS = 500L
        const val EXIT_DELAY = 2100L
        const val EXIT_MS = 200L

        const val TEXT_START_SCALE = 0.94f
        const val BLUR_START_RADIUS = 22f

        /** Brighter core of the gold light sweep. */
        val GOLD_HIGHLIGHT = Color.parseColor("#F6E3A1")
    }
}
