package dev.callzik.dotifywidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Настройка доступа, выбор оформления с живым превью и добавление виджетов. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var grant: Button
    private lateinit var beatSwitch: android.widget.Switch
    private lateinit var previewFull: FrameLayout
    private lateinit var previewCompact: FrameLayout

    private val ink = 0xFF100E17.toInt()
    private val card = 0xFF1B1826.toInt()
    private val accent = 0xFFB794FF.toInt()
    private val muted = 0xFFA39FB8.toInt()

    private companion object {
        const val REQ_AUDIO = 7
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink
        window.navigationBarColor = ink

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }

        col.addView(text("Dotify Widget", 26f, Color.WHITE, bold = true))
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        col.addView(text("Виджет «сейчас играет» для главного экрана · v$version", 14f, muted).apply { setPadding(0, dp(4), 0, dp(20)) })

        // Доступ
        status = text("", 14f, Color.WHITE)
        grant = button("Дать доступ к уведомлениям") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        col.addView(section(status, grant))

        // Превью
        col.addView(header("Превью"))
        previewFull = FrameLayout(this)
        previewCompact = FrameLayout(this)
        // Подложка «как обои», чтобы стекло и прозрачные темы было видно
        val backdrop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3A2C7A.toInt(), 0xFFB0407A.toInt(), 0xFFE8925A.toInt())).apply { cornerRadius = dp(26).toFloat() }
            addView(previewFull, LinearLayout.LayoutParams(-1, dp(184)))
            addView(previewCompact, LinearLayout.LayoutParams(-1, dp(70)).apply { topMargin = dp(12) })
        }
        col.addView(backdrop)

        // Оформление
        col.addView(header("Оформление"))
        val group = RadioGroup(this)
        val current = WidgetTheme.current(this)
        WidgetTheme.entries.forEachIndexed { i, t ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = t
                text = "${t.label}\n"
                append(android.text.SpannableString(t.hint).apply {
                    setSpan(android.text.style.ForegroundColorSpan(muted), 0, length, 0)
                    setSpan(android.text.style.RelativeSizeSpan(0.82f), 0, length, 0)
                })
                setTextColor(Color.WHITE)
                textSize = 15f
                buttonTintList = ColorStateList.valueOf(accent)
                setPadding(dp(8), dp(10), 0, dp(10))
                isChecked = t == current
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            val t = g.findViewById<RadioButton>(checkedId)?.tag as? WidgetTheme ?: return@setOnCheckedChangeListener
            WidgetTheme.save(this, t)
            Widgets.updateAll(this)
            renderPreview()
        }
        val wave = android.widget.Switch(this).apply {
            text = "Переливающийся фон, как в «Моей волне»"
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = Wave.enabled(this@MainActivity)
            thumbTintList = ColorStateList.valueOf(accent)
            setPadding(dp(8), dp(14), 0, dp(6))
            setOnCheckedChangeListener { _, on ->
                Wave.setEnabled(this@MainActivity, on)
                Widgets.updateAll(this@MainActivity)
                renderPreview()
            }
        }
        beatSwitch = android.widget.Switch(this).apply {
            text = "Фон течёт в темпе музыки\n"
            append(android.text.SpannableString("Громче — быстрее. Нужен доступ к микрофону: читается только громкость, звук не записывается").apply {
                setSpan(android.text.style.ForegroundColorSpan(muted), 0, length, 0)
                setSpan(android.text.style.RelativeSizeSpan(0.82f), 0, length, 0)
            })
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = Beat.enabled(this@MainActivity)
            thumbTintList = ColorStateList.valueOf(accent)
            setPadding(dp(8), dp(10), 0, dp(6))
            setOnCheckedChangeListener { _, on ->
                if (on && !Beat.hasPermission(this@MainActivity)) {
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
                } else {
                    Beat.setEnabled(this@MainActivity, on)
                    MediaListener.instance?.refreshBeat()
                }
            }
        }
        col.addView(section(group, wave, beatSwitch))

        // Добавление на экран
        col.addView(header("Добавить на главный экран"))
        col.addView(section(
            button("Большой виджет 4×2") { pin(NowPlayingWidget::class.java) },
            button("Мини-виджет 4×1") { pin(CompactWidget::class.java) },
            text("Если кнопки не срабатывают: долгий тап по главному экрану → Виджеты → Dotify Widget.", 13f, muted).apply { setPadding(0, dp(8), 0, 0) },
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(ink)
            addView(col)
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQ_AUDIO) return
        val ok = grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
        Beat.setEnabled(this, ok)
        beatSwitch.isChecked = ok
        if (!ok) Toast.makeText(this, "Без доступа к микрофону фон не сможет реагировать на музыку", Toast.LENGTH_LONG).show()
        MediaListener.instance?.refreshBeat()
    }

    override fun onResume() {
        super.onResume()
        val ok = MediaListener.hasAccess(this)
        status.text = if (ok) "Доступ есть. Виджет видит, что играет в Dotify." else "Нужен доступ к уведомлениям: через него Android сообщает, какой трек играет."
        grant.visibility = if (ok) View.GONE else View.VISIBLE
        NowPlaying.refresh(this)
        Widgets.updateAll(this)
        renderPreview()
    }

    private fun renderPreview() {
        previewFull.removeAllViews()
        previewCompact.removeAllViews()
        runCatching {
            previewFull.addView(Widgets.build(this, R.layout.widget_full, NowPlayingWidget::class.java, previewSize(184)).apply(this, previewFull))
            previewCompact.addView(Widgets.build(this, R.layout.widget_compact, CompactWidget::class.java, previewSize(70)).apply(this, previewCompact))
        }
    }

    private fun previewSize(heightDp: Int): Pair<Int, Int> {
        val m = resources.displayMetrics
        return (m.widthPixels / m.density).toInt() - 40 to heightDp
    }

    private fun pin(cls: Class<*>) {
        val m = getSystemService(AppWidgetManager::class.java)
        if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(this, cls), null, null)
        else Toast.makeText(this, "Добавь виджет вручную через меню главного экрана", Toast.LENGTH_LONG).show()
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
    }

    private fun header(s: String) = text(s.uppercase(), 12f, muted).apply {
        letterSpacing = 0.12f
        setPadding(dp(4), dp(24), 0, dp(10))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(ink)
        textSize = 15f
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(accent) }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) }
        setOnClickListener { onClick() }
    }

    private fun section(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(card) }
        views.forEach { addView(it) }
    }
}
