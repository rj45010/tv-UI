package com.rahul.home

import android.app.Activity
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.storage.StorageManager
import android.text.format.DateUtils
import android.text.format.Formatter
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MainActivity : Activity() {

    private val favourites = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.apple.atve.androidtv.appletv",
        "com.amazon.amazonvideo.livingroom",
        "in.startv.hotstar",
    )

    private lateinit var root: FrameLayout
    private lateinit var lava: LavaView
    private lateinit var homeView: View
    private lateinit var title: TextView
    private lateinit var weather: TextView
    private lateinit var leftStatus: View
    private lateinit var greeting: TextView
    private val greetingTick = object : Runnable {
        override fun run() { updateGreeting(); root.postDelayed(this, 60_000) }
    }
    private var weatherFetchedAt = 0L
    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout
    private var listView: View? = null
    private var lastFocusKey: String? = null
    private var listIndex = 0

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        lava = LavaView(this)
        root.addView(lava, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(24), dp(20), dp(24), dp(36))
            clipChildren = false
            clipToPadding = false
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            isAllCaps = true
            letterSpacing = 0.12f
            setTextColor(0xD9FFFFFF.toInt())
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        weather = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            setPadding(0, dp(4), 0, 0)
        }
        leftStatus = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(date); addView(weather)
        }
        val time = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "h:mm"
            setTextColor(Color.WHITE)
            textSize = 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
        }
        val amPm = TextClock(this).apply {
            format12Hour = "a"
            format24Hour = "a"
            isAllCaps = true
            setTextColor(0xD9FFFFFF.toInt())
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(6), 0, 0, dp(5))
        }
        val rightStatus = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            addView(time); addView(amPm)
        }
        title = TextView(this).apply {
            textSize = 44f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(24f, 0f, 2f, 0x99000000.toInt())
            setPadding(dp(8), 0, 0, dp(14))
        }
        row1 = card()
        row2 = card()
        content.addView(title)
        content.addView(row1, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(row2, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(14) })
        homeView = content
        root.addView(homeView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        greeting = TextView(this).apply {
            textSize = 26f
            letterSpacing = 0.02f
            setTextColor(0xF2FFFFFF.toInt())
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(18f, 0f, 2f, 0x66000000)
        }
        root.addView(greeting, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(34)
        })
        root.addView(leftStatus, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(28); leftMargin = dp(32)
        })
        root.addView(rightStatus, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(24); rightMargin = dp(32)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        lava.start()
        greetingTick.run()
        fetchWeather()
        if (listView == null) renderHome() else {
            root.removeView(listView)
            listView = null
            openList()
        }
    }

    private fun fetchWeather() {
        val now = SystemClock.elapsedRealtime()
        if (weatherFetchedAt != 0L && now - weatherFetchedAt < 30 * 60_000) return
        weatherFetchedAt = now
        Thread {
            try {
                val geo = JSONObject(httpGet("https://ipwho.is/"))
                val lat = geo.getDouble("latitude")
                val lon = geo.getDouble("longitude")
                val wx = JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day"))
                val current = wx.getJSONObject("current")
                val temp = current.getDouble("temperature_2m").roundToInt()
                val (icon, label) = describeWeather(current.getInt("weather_code"), current.getInt("is_day") == 1)
                runOnUiThread { weather.text = "$icon  $temp°C  $label" }
            } catch (e: Exception) {
                weatherFetchedAt = 0L
            }
        }.start()
    }

    private fun updateGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        greeting.text = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    private fun describeWeather(code: Int, day: Boolean): Pair<String, String> = when (code) {
        0 -> (if (day) "☀️" else "🌙") to "Clear"
        1, 2 -> (if (day) "🌤️" else "☁️") to "Partly cloudy"
        3 -> "☁️" to "Cloudy"
        45, 48 -> "🌫️" to "Fog"
        in 51..67, in 80..82 -> "🌧️" to "Rain"
        in 71..77, 85, 86 -> "❄️" to "Snow"
        in 95..99 -> "⛈️" to "Thunderstorm"
        else -> "🌡️" to ""
    }

    private fun launch(intent: Intent?) {
        if (intent == null) return
        try { startActivity(intent) } catch (e: ActivityNotFoundException) { } catch (e: SecurityException) { }
    }

    private fun httpGet(url: String): String = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 8000
        readTimeout = 8000
        try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
    }

    override fun onPause() {
        super.onPause()
        lava.stop()
        root.removeCallbacks(greetingTick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (listView != null) closeList("more")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (listView != null) closeList(null)
    }


    private fun apps(): Map<String, ResolveInfo> = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
    ).filter { it.activityInfo.packageName != packageName }
        .associateBy { it.activityInfo.packageName }

    private fun renderHome() {
        val apps = apps()
        row1.removeAllViews()
        row2.removeAllViews()
        favourites.mapNotNull { apps[it] }.forEach { row1.addView(appTile(it)) }
        row2.addView(textTile("more", "All Apps", "All Apps") { openList() })
        row2.addView(textTile("wifi", currentSsid(), "Wi-Fi") { launch(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        row2.addView(textTile("bluetooth", "Bluetooth", "Bluetooth") {
            launch(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"))
        })
        row2.addView(textTile("inputs", "Inputs", "Inputs") { openInputs() })
        row2.addView(textTile("settings", "Settings", "Settings") { launch(Intent(Settings.ACTION_SETTINGS)) })
        val all = (0 until row1.childCount).map { row1.getChildAt(it) } + (0 until row2.childCount).map { row2.getChildAt(it) }
        (all.firstOrNull { it.tag == lastFocusKey } ?: all.firstOrNull())?.requestFocus()
    }

    private fun openList() {
        val apps = apps().values.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) return
        listIndex = listIndex.coerceIn(0, apps.lastIndex)
        val wheel = FrameLayout(this).apply { isFocusable = true }
        wheel.addView(TextView(this).apply {
            text = "All Apps"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(48); topMargin = dp(30) })
        wheel.addView(TextView(this).apply {
            text = "${apps.size} apps"
            textSize = 15f
            setTextColor(0x99FFFFFF.toInt())
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(50); topMargin = dp(72) })
        val lineX = dp(164 - 24).toFloat()
        wheel.addView(object : View(this) {
            private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat() }
            private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            override fun onDraw(canvas: Canvas) {
                val cy = height / 2f + dp(60)
                val path = Path()
                var o = -3.8f
                while (o <= 3.8f) {
                    val x = lineX - (1f - cos(o * 0.3f)) * dp(200)
                    val y = cy + o * dp(84) * (1f - 0.05f * abs(o))
                    if (o == -3.8f) path.moveTo(x, y) else path.lineTo(x, y)
                    o += 0.1f
                }
                line.shader = LinearGradient(0f, cy - dp(220), 0f, cy + dp(220),
                    intArrayOf(Color.TRANSPARENT, 0x99FFFFFF.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawPath(path, line)
                canvas.drawCircle(lineX, cy, dp(5).toFloat(), dot)
            }
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val slots = (-4..4).map {
            val icon = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, v.width * 0.225f)
                }
            }
            val name = TextView(this).apply {
                setTextColor(Color.WHITE)
                setPadding(dp(20), 0, 0, 0)
                maxLines = 1
            }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                pivotX = 0f
                addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
                addView(name)
            }
            wheel.addView(row, FrameLayout.LayoutParams(dp(420), dp(64), Gravity.CENTER_VERTICAL).apply {
                leftMargin = dp(164); topMargin = dp(60)
            })
            Triple(row, icon, name)
        }

        val radius = dp(18).toFloat()
        val banner = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, radius)
            }
        }
        val cardName = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(16), 0, dp(6))
            maxLines = 1
        }
        val lastUsed = TextView(this).apply { textSize = 15f; setTextColor(0xB3FFFFFF.toInt()) }
        val size = TextView(this).apply { textSize = 15f; setTextColor(0xB3FFFFFF.toInt()); setPadding(0, dp(2), 0, 0) }
        fun current() = apps[listIndex].activityInfo.packageName
        val actions = LinearLayout(this).apply { setPadding(0, dp(18), 0, 0) }
        val openBtn = pill("Open", Color.WHITE) { launch(packageManager.getLeanbackLaunchIntentForPackage(current())) }
        val infoBtn = pill("Info", Color.WHITE) {
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${current()}")))
        }
        val removeBtn = pill("Uninstall", 0xFFFF6B6B.toInt()) {
            launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:${current()}")))
        }
        listOf(openBtn, infoBtn, removeBtn).forEach { btn ->
            actions.addView(btn)
            btn.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when {
                    keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> { wheel.requestFocus(); true }
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT && btn === openBtn -> { wheel.requestFocus(); true }
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && btn === removeBtn -> true
                    else -> false
                }
            }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(32).toFloat()
                setColor(0xE6141418.toInt())
                setStroke(dp(1), 0x2EFFFFFF)
            }
            elevation = dp(10).toFloat()
            addView(banner, LinearLayout.LayoutParams(MATCH_PARENT, dp(168)))
            addView(cardName); addView(lastUsed); addView(size); addView(actions)
        }
        wheel.addView(card, FrameLayout.LayoutParams(dp(340), WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.END).apply {
            rightMargin = dp(48); topMargin = dp(60)
        })

        fun updateCard() {
            val app = apps[listIndex]
            val pkg = app.activityInfo.packageName
            banner.setImageDrawable(app.activityInfo.loadBanner(packageManager)
                ?: app.activityInfo.applicationInfo.loadBanner(packageManager)
                ?: app.loadIcon(packageManager))
            cardName.text = app.loadLabel(packageManager)
            lastUsed.text = ""
            size.text = ""
            val isSystem = app.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            removeBtn.visibility = if (isSystem) View.GONE else View.VISIBLE
            val shownIndex = listIndex
            Thread {
                val used = lastUsedText(pkg)
                val bytes = sizeText(pkg)
                runOnUiThread {
                    if (listIndex != shownIndex) return@runOnUiThread
                    lastUsed.text = used
                    size.text = bytes
                }
            }.start()
        }

        fun layoutWheel() {
            slots.forEachIndexed { i, (row, icon, name) ->
                val offset = i - 4
                val app = apps.getOrNull(listIndex + offset)
                row.visibility = if (app == null) View.INVISIBLE else View.VISIBLE
                if (app == null) return@forEachIndexed
                val far = abs(offset).toFloat()
                icon.setImageDrawable(app.loadIcon(packageManager))
                name.text = app.loadLabel(packageManager)
                name.textSize = if (offset == 0) 34f else 24f
                name.typeface = Typeface.create(if (offset == 0) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
                row.translationY = offset * dp(84) * (1f - 0.05f * far)
                row.translationX = -(1f - cos(offset * 0.3f)) * dp(200)
                row.scaleX = 1f - 0.1f * far
                row.scaleY = row.scaleX
                row.alpha = if (far >= 3f) 0f else 1f - 0.3f * far
                row.cameraDistance = 12000f * resources.displayMetrics.density
                row.rotationX = -offset * 5f
            }
            updateCard()
        }

        wheel.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { if (listIndex > 0) { listIndex--; layoutWheel() }; true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (listIndex < apps.lastIndex) { listIndex++; layoutWheel() }; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { openBtn.requestFocus(); true }
                KeyEvent.KEYCODE_DPAD_LEFT -> true
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    launch(packageManager.getLeanbackLaunchIntentForPackage(current()))
                    true
                }
                else -> false
            }
        }

        layoutWheel()
        homeView.visibility = View.GONE
        leftStatus.visibility = View.GONE
        greeting.visibility = View.GONE
        root.addView(wheel, 1, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        listView = wheel
        wheel.requestFocus()
    }

    private fun pill(text: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(color)
        gravity = Gravity.CENTER
        isFocusable = true
        setPadding(dp(16), dp(9), dp(16), dp(9))
        val idle = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0x26FFFFFF) }
        val focused = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.WHITE) }
        background = idle
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = dp(10) }
        setOnFocusChangeListener { _, has ->
            background = if (has) focused else idle
            setTextColor(if (has) Color.BLACK else color)
        }
        setOnClickListener { onClick() }
    }

    private fun lastUsedText(pkg: String): String {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return ""
        val now = System.currentTimeMillis()
        val last = usm.queryAndAggregateUsageStats(now - 60L * 24 * 3600 * 1000, now)[pkg]?.lastTimeUsed ?: 0L
        return if (last <= 0L) "Not opened recently"
        else "Opened " + DateUtils.getRelativeTimeSpanString(last, now, DateUtils.MINUTE_IN_MILLIS)
    }

    private fun sizeText(pkg: String): String = try {
        val stats = getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
        "Uses " + Formatter.formatShortFileSize(this, stats.appBytes + stats.dataBytes + stats.cacheBytes)
    } catch (e: Exception) {
        ""
    }

    private fun closeList(focusKey: String?) {
        root.removeView(listView)
        listView = null
        homeView.visibility = View.VISIBLE
        leftStatus.visibility = View.VISIBLE
        greeting.visibility = View.VISIBLE
        lastFocusKey = focusKey
        renderHome()
    }

    private fun card() = LinearLayout(this).apply {
        setPadding(dp(8), dp(10), dp(8), dp(10))
        clipChildren = false
        clipToPadding = false
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF5141417.toInt(), 0xF50B0B0D.toInt())).apply {
            cornerRadius = dp(30).toFloat()
            setStroke(dp(1), 0x2EFFFFFF)
        }
        elevation = dp(6).toFloat()
    }

    private fun appTile(info: ResolveInfo): View {
        val pkg = info.activityInfo.packageName
        val art: Drawable = info.activityInfo.loadBanner(packageManager)
            ?: info.activityInfo.applicationInfo.loadBanner(packageManager)
            ?: info.loadIcon(packageManager)
        val image = ImageView(this).apply {
            setImageDrawable(art)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return tile(pkg, info.loadLabel(packageManager).toString(), image) {
            launch(packageManager.getLeanbackLaunchIntentForPackage(pkg))
        }
    }

    private fun textTile(key: String, name: String, glyph: String, onClick: () -> Unit): View {
        val label = TextView(this).apply {
            text = glyph
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF3A3A44.toInt())
        }
        return tile(key, name, label, onClick)
    }

    private fun tile(key: String, name: String, art: View, onClick: () -> Unit): View {
        val radius = dp(16).toFloat()
        val rounded = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, radius)
        }
        art.clipToOutline = true
        art.outlineProvider = rounded
        if (art.background == null) art.setBackgroundColor(0xFF1C1C22.toInt())
        art.foreground = GradientDrawable().apply { this.cornerRadius = radius; setStroke(dp(1), 0x47FFFFFF) }
        val tileWidth = (resources.displayMetrics.widthPixels - dp(24 * 2 + 8 * 2 + 12 * 5)) / 5
        val ring = GradientDrawable().apply { cornerRadius = dp(21).toFloat(); setStroke(dp(3), Color.WHITE) }
        return FrameLayout(this).apply {
            tag = key
            isFocusable = true
            setPadding(dp(5), dp(5), dp(5), dp(5))
            addView(art, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            layoutParams = LinearLayout.LayoutParams(tileWidth, tileWidth * 9 / 16).apply {
                setMargins(dp(6), dp(2), dp(6), dp(2))
            }
            setOnFocusChangeListener { v, has ->
                v.background = if (has) ring else null
                if (has) {
                    lastFocusKey = key
                    title.text = name
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun currentSsid(): String {
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        @Suppress("DEPRECATION")
        val ssid = wifi?.connectionInfo?.ssid?.trim('"')
        return if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") "Wi-Fi" else ssid
    }

    private fun openInputs() {
        val intent = packageManager.getLeanbackLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            ?: packageManager.getLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            ?: Intent(Settings.ACTION_SETTINGS)
        launch(intent)
    }
}

private class LavaView(context: Activity) : View(context) {
    private val blobs = arrayOf(
        floatArrayOf(0.16f, 0.22f, 0.0f, 0.0f),
        floatArrayOf(0.13f, 0.19f, 2.1f, 1.6f),
        floatArrayOf(0.19f, 0.11f, 4.2f, 3.2f),
        floatArrayOf(0.10f, 0.16f, 5.5f, 4.7f),
    )
    private val palette = intArrayOf(
        0xFF7A1F2B.toInt(), 0xFF9C4A2E.toInt(), 0xFFA8793A.toInt(), 0xFF2F6B5E.toInt(),
        0xFF1F5F73.toInt(), 0xFF2B4A80.toInt(), 0xFF4A3C80.toInt(), 0xFF5C2E4F.toInt(),
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun paletteAt(x: Float): Int {
        val f = ((x % palette.size) + palette.size) % palette.size
        val a = palette[f.toInt()]
        val b = palette[(f.toInt() + 1) % palette.size]
        val k = f - f.toInt()
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).toInt() shl shift
        return ch(16) or ch(8) or ch(0)
    }
    private val shade = Paint()
    private val epoch = SystemClock.uptimeMillis()
    private var running = false

    fun start() { running = true; invalidate() }
    fun stop() { running = false }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shade.shader = LinearGradient(0f, h * 0.3f, 0f, h.toFloat(), Color.TRANSPARENT, 0x59000000, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - epoch) / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        val base = paletteAt(t * 0.05f + 0.5f)
        canvas.drawColor(Color.rgb(Color.red(base) * 3 / 5, Color.green(base) * 3 / 5, Color.blue(base) * 3 / 5))
        for ((i, blob) in blobs.withIndex()) {
            val (sx, sy, phase) = blob
            val cx = w * (0.5f + 0.4f * sin(t * sx + phase))
            val cy = h * (0.45f + 0.4f * cos(t * sy + phase * 1.3f))
            val r = w * (0.5f + 0.1f * sin(t * 0.45f + phase))
            val c = paletteAt(t * 0.05f + i * 1.3f)
            paint.shader = RadialGradient(cx, cy, r, intArrayOf(c or 0xE6000000.toInt(), c or 0x80000000.toInt(), c), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
        }
        canvas.drawRect(0f, 0f, w, h, shade)
        if (running) postInvalidateDelayed(40)
    }
}
