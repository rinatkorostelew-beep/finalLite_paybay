package com.bighead.app

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bighead.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val REQ = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        animIn()
        b.btnLaunch.setOnClickListener { onLaunchClick() }
        b.btnLogout.setOnClickListener { onLogoutClick() }
    }

    override fun onResume() {
        super.onResume()
        refreshBtn()
    }

    private fun animIn() {
        listOf(b.ivMainLogo, b.tvMainTitle, b.tvMainSub, b.btnLaunch, b.btnLogout)
            .forEachIndexed { i, v ->
                v.alpha = 0f; v.translationY = 28f
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(90L * i).setDuration(380)
                    .setInterpolator(OvershootInterpolator(1.1f)).start()
            }
    }

    private fun refreshBtn() {
        b.btnLaunch.text = if (running()) "ОСТАНОВИТЬ ИНЖЕКТ" else "INJECT"
    }

    private fun onLaunchClick() {
        bounce(b.btnLaunch)
        when {
            running() -> {
                OverlayService.stop(this)
                refreshBtn()
            }
            !hasOverlayPerm() -> askOverlayPerm()
            else -> {
                launchGame("com.axlebolt.standoff2")
                b.btnLaunch.postDelayed({
                    launch()
                }, 1500)
            }
        }
    }

    private fun onLogoutClick() {
        try { OverlayService.stop(this) } catch (_: Exception) {}
        getSharedPreferences("bighead_prefs", MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, KeyActivity::class.java))
        finish()
    }

    // ── Разрешение SYSTEM_ALERT_WINDOW ────────────────────────────────────────

    private fun hasOverlayPerm() = Settings.canDrawOverlays(this)

    private fun askOverlayPerm() {
        toast("Выдай разрешение и вернись в приложение")
        try {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")), REQ
            )
        } catch (_: Exception) {
            toast("Настройки → Приложения → BigHead → Поверх других приложений")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) {
            if (hasOverlayPerm()) {
                launchGame("com.axlebolt.standoff2")
                b.btnLaunch.postDelayed({
                    launch()
                }, 1500)
            } else {
                toast("Разрешение не выдано — оверлей недоступен")
            }
            refreshBtn()
        }
    }

    private fun launch() {
        try {
            OverlayService.start(this)
            refreshBtn()
        } catch (e: Exception) {
            toast("Ошибка: ${e.message}")
        }
    }

    private fun launchGame(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                toast("Запуск Standoff 2...")
            } else {
                toast("Standoff 2 не установлен")
            }
        } catch (e: Exception) {
            toast("Ошибка запуска: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun running(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                .getRunningServices(100)
                .any { it.service.className == OverlayService::class.java.name }
        } catch (_: Exception) { false }
    }

    private fun bounce(v: View) {
        v.animate().scaleX(.94f).scaleY(.94f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                .setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
