package com.nusantarafighter

import android.app.Activity
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        @Suppress("DEPRECATION")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        gameView = GameView(this)
        setContentView(gameView)

        goImmersive()
    }

    /** True edge-to-edge immersive mode: draws behind the status/nav bars and
     *  auto-hides them again after a swipe reveal, on every Android version
     *  from minSdk up (handled by the androidx compat layer). */
    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, gameView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    override fun onResume() {
        super.onResume()
        goImmersive()
    }
}
