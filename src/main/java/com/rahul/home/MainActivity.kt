package com.rahul.home

import android.app.Activity
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
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
import kotlin.math.roundToInt
import kotlin.random.Random

private const val INK = 0xFF2E2E2C.toInt()
private const val MUTED = 0xFF8C8B87.toInt()
private const val BACKDROP = 0xFFE9E8E4.toInt()
private const val TRAY = 0x8C1C1B1F.toInt()
private const val TONAL = 0x66FFFFFF.toInt()
private const val ICON_BG = 0xF02A292F.toInt()
private const val ON_TONAL = 0xFFE6E1E5.toInt()

class MainActivity : Activity() {

    private val favourites = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.apple.atve.androidtv.appletv",
        "com.amazon.amazonvideo.livingroom",
        "in.startv.hotstar",
    )

    private lateinit var root: FrameLayout
    private lateinit var homeView: View
    private lateinit var weather: TextView
    private lateinit var leftStatus: View
    private lateinit var rightStatus: View
    private lateinit var pet: Pet
    private lateinit var cat: Pet
    private lateinit var greeting: TextView
    private val greetingTick = object : Runnable {
        override fun run() { updateGreeting(); root.postDelayed(this, 60_000) }
    }
    private var weatherFetchedAt = 0L
    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout
    private lateinit var glass: Glass
    private var listView: View? = null
    private var lastFocusKey: String? = null
    private var listIndex = 0

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(BACKDROP) }
        root.addView(ImageView(this).apply {
            resources.getIdentifier("bg", "drawable", packageName).takeIf { it != 0 }?.let { setImageResource(it) }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(36))
            clipChildren = false
            clipToPadding = false
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            isAllCaps = true
            letterSpacing = 0.12f
            setTextColor(MUTED)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        weather = TextView(this).apply {
            setTextColor(INK)
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
            setTextColor(INK)
            textSize = 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
        }
        val amPm = TextClock(this).apply {
            format12Hour = "a"
            format24Hour = "a"
            isAllCaps = true
            setTextColor(MUTED)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(6), 0, 0, dp(5))
        }
        rightStatus = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            addView(time); addView(amPm)
        }
        row1 = card()
        pet = Pet(this, RIGHT_PET)
        cat = Pet(this, LEFT_PET)
        row1.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            // Pets draw in a layer that starts at the content padding; inset by the tray's corner radius so they never stand on the curve.
            val left = l - content.paddingLeft + dp(28f)
            val right = r - content.paddingLeft - dp(28f)
            val mid = (left + right) / 2f
            cat.laneLeft = left
            cat.laneRight = mid - dp(4f)
            pet.laneLeft = mid + dp(4f)
            pet.laneRight = right
        }
        content.addView(FrameLayout(this).apply {
            addView(cat, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(pet, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)))
        content.addView(row1, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        homeView = content
        root.addView(homeView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        row2 = LinearLayout(this).apply {
            setPadding(dp(6), dp(10), dp(6), dp(10))
            background = Flat(TRAY, dp(36).toFloat())
        }
        root.addView(row2, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(18)
        })
        greeting = TextView(this).apply {
            textSize = 22f
            letterSpacing = 0.02f
            setTextColor(INK)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        root.addView(greeting, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(102)
        })
        root.addView(leftStatus, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(28); leftMargin = dp(32)
        })
        root.addView(rightStatus, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(24); rightMargin = dp(32)
        })
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now ->
            glass.target = if (now?.parent === row1 || now?.parent === row2) now else null
        }
        glass = Glass(this)
        root.addView(glass, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        pet.start()
        cat.start()
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
        greeting.text = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
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
        pet.stop()
        cat.stop()
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
        row2.addView(iconTile("more", R.drawable.ic_apps, dp(48)) { openList() })
        row2.addView(iconTile("wifi", R.drawable.ic_wifi, dp(48)) { launch(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        row2.addView(iconTile("bluetooth", R.drawable.ic_bluetooth, dp(48)) {
            launch(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"))
        })
        row2.addView(iconTile("inputs", R.drawable.ic_input, dp(48)) { openInputs() })
        row2.addView(iconTile("settings", R.drawable.ic_settings, dp(48)) { launch(Intent(Settings.ACTION_SETTINGS)) })
        for (i in 0 until minOf(row1.childCount, row2.childCount)) {
            val app = row1.getChildAt(i).apply { id = View.generateViewId() }
            val quick = row2.getChildAt(i).apply { id = View.generateViewId() }
            app.nextFocusUpId = quick.id
            quick.nextFocusDownId = app.id
        }
        val all = listOf(row1, row2).flatMap { g -> (0 until g.childCount).map { g.getChildAt(it) } }
        (all.firstOrNull { it.tag == lastFocusKey } ?: all.firstOrNull())?.requestFocus()
    }

    private fun openList() {
        val apps = apps().values.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (apps.isEmpty()) return
        listIndex = listIndex.coerceIn(0, apps.lastIndex)
        val wheel = FrameLayout(this).apply {
            isFocusable = true
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xE6121214.toInt(), 0x99121214.toInt()))
        }
        wheel.addView(TextView(this).apply {
            text = "All Apps"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(48); topMargin = dp(36) })
        wheel.addView(TextView(this).apply {
            text = "${apps.size} apps"
            textSize = 15f
            setTextColor(0x99FFFFFF.toInt())
        }, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(49); topMargin = dp(78) })
        val highlight = object : View(this) {
                    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
            private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = Color.WHITE }
            override fun onDraw(canvas: Canvas) {
                val cy = height / 2f + dp(40)
                val r = dp(36).toFloat()
                val rect = RectF(dp(36).toFloat(), cy - r, dp(36 + 400).toFloat(), cy + r)
                canvas.drawRoundRect(rect, r, r, fill)
                if (wheel.isFocused) canvas.drawRoundRect(rect, r, r, ring)
            }
        }
        wheel.addView(highlight, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wheel.setOnFocusChangeListener { _, _ -> highlight.invalidate() }

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
                setPadding(dp(18), 0, 0, 0)
                maxLines = 1
            }
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                pivotX = 0f
                addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(name)
            }
            wheel.addView(row, FrameLayout.LayoutParams(dp(370), dp(64), Gravity.CENTER_VERTICAL).apply { leftMargin = dp(52); topMargin = dp(40) })
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
        val openBtn = pill("Open", ON_TONAL) { launch(packageManager.getLeanbackLaunchIntentForPackage(current())) }
        val infoBtn = pill("Info", ON_TONAL) {
            launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${current()}")))
        }
        val removeBtn = pill("Uninstall", 0xFFF2B8B5.toInt()) {
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
            background = Flat(TRAY, dp(28).toFloat())
            addView(banner, LinearLayout.LayoutParams(MATCH_PARENT, dp(168)))
            addView(cardName); addView(lastUsed); addView(size); addView(actions)
        }
        wheel.addView(card, FrameLayout.LayoutParams(dp(340), WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.END).apply { rightMargin = dp(48) })

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
                name.textSize = if (offset == 0) 26f else 20f
                name.typeface = Typeface.create(if (offset == 0) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
                row.translationY = offset * dp(72).toFloat()
                row.scaleX = 1f - 0.06f * far
                row.scaleY = row.scaleX
                row.alpha = if (far >= 3f) 0f else 1f - 0.3f * far
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
        rightStatus.visibility = View.GONE
        row2.visibility = View.GONE
        pet.visibility = View.GONE
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
        val idle = Flat(TONAL, dp(20).toFloat())
        val focused = Flat(Color.WHITE, dp(20).toFloat())
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
        rightStatus.visibility = View.VISIBLE
        row2.visibility = View.VISIBLE
        pet.visibility = View.VISIBLE
        greeting.visibility = View.VISIBLE
        pet.start()
        cat.start()
        lastFocusKey = focusKey
        renderHome()
    }

    private fun card() = LinearLayout(this).apply {
        setPadding(dp(14), dp(16), dp(14), dp(16))
        clipChildren = false
        clipToPadding = false
        background = Flat(TRAY, dp(28).toFloat())
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
        return tile(pkg, image, dp(136), dp(77), Color.TRANSPARENT, dp(12).toFloat()) {
            launch(packageManager.getLeanbackLaunchIntentForPackage(pkg))
        }
    }

    private fun iconTile(key: String, icon: Int, size: Int = dp(56), onClick: () -> Unit): View {
        val image = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(ON_TONAL)
            setPadding(size * 2 / 7, size * 2 / 7, size * 2 / 7, size * 2 / 7)
        }
        return tile(key, image, size, size, ICON_BG, size / 2f, onClick)
    }

    private fun tile(key: String, art: View, width: Int, height: Int, fill: Int, radius: Float, onClick: () -> Unit): View {
        val rounded = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, radius)
        }
        art.clipToOutline = true
        art.outlineProvider = rounded
        return FrameLayout(this).apply {
            tag = key
            isFocusable = true
            background = Flat(fill, radius)
            addView(art, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            layoutParams = LinearLayout.LayoutParams(width, height).apply {
                setMargins(dp(6), dp(2), dp(6), dp(2))
            }
            setOnFocusChangeListener { v, has ->
                v.scaleX = if (has) 1.06f else 1f
                v.scaleY = v.scaleX
                if (has) {
                    lastFocusKey = key
                }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun openInputs() {
        val intent = packageManager.getLeanbackLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            ?: packageManager.getLaunchIntentForPackage("com.mediatek.wwtv.tvcenter")
            ?: Intent(Settings.ACTION_SETTINGS)
        launch(intent)
    }
}

private class Flat(private val fill: Int, val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        paint.color = fill
        canvas.drawRoundRect(RectF(bounds), radius, radius, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

private class Glass(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = Color.WHITE }
    private val from = RectF()
    private val current = RectF()
    private val to = RectF()
    private val loc = IntArray(2)
    private val own = IntArray(2)
    private var start = 0L
    private var placed = false

    var target: View? = null
        set(v) {
            if (v != null && placed) from.set(current) else placed = false
            start = SystemClock.uptimeMillis()
            field = v
            invalidate()
        }

    // Reads the target's position every frame so it follows tiles while the tray is still moving.
    override fun onDraw(canvas: Canvas) {
        val t = target ?: return
        if (!t.isShown) return
        t.getLocationInWindow(loc)
        getLocationInWindow(own)
        val gap = 5 * d
        val x = (loc[0] - own[0]).toFloat()
        val y = (loc[1] - own[1]).toFloat()
        to.set(x - gap, y - gap, x + t.width * t.scaleX + gap, y + t.height * t.scaleY + gap)
        val k = ((SystemClock.uptimeMillis() - start) / 220f).coerceIn(0f, 1f)
        val e = 1 - (1 - k) * (1 - k) * (1 - k)
        if (!placed) { from.set(to); placed = true }
        current.set(
            from.left + (to.left - from.left) * e, from.top + (to.top - from.top) * e,
            from.right + (to.right - from.right) * e, from.bottom + (to.bottom - from.bottom) * e,
        )
        val r = ((t.background as? Flat)?.radius ?: 12 * d) * t.scaleX + gap
        canvas.drawRoundRect(current, r, r, ring)
        if (k < 1f) postInvalidateOnAnimation()
    }
}

// Which rows of an openpets.dev sheet a pet uses. Sheets are looked up by file name in res/drawable-nodpi and are not in the repo. Sheets are pre-processed so every frame's feet sit on the cell floor.
private class PetSpec(
    val sheet: String,
    val frames: IntArray,
    val walkLeft: Int,
    val walkRight: Int,
    val flipRight: Boolean,
    val actions: IntArray,
    val napRow: Int,
    val napHold: Int,
)

// Rows tuned for Cinder from openpets.dev. See docs/pets.md to fit another pet.
private val RIGHT_PET = PetSpec("pet_right", intArrayOf(6, 8, 8, 4, 5, 8, 6, 6, 6), 2, 1, false, intArrayOf(0, 3, 4, 5, 6, 7, 8), 5, 4)
// Rows tuned for neko: no nap, and its "failed" row (5, crying) is left out.
private val LEFT_PET = PetSpec("pet_left", intArrayOf(6, 8, 8, 4, 5, 8, 6, 6, 6), 2, 1, false, intArrayOf(0, 3, 4, 6, 7, 8), -1, -1)

private class Pet(context: Context, private val spec: PetSpec) : View(context) {
    private val sheet = resources.getIdentifier(spec.sheet, "drawable", context.packageName)
        .takeIf { it != 0 }?.let { BitmapFactory.decodeResource(resources, it) }
    private val cellW = (sheet?.width ?: 8) / 8
    private val cellH = (sheet?.height ?: 9) / 9
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var row = 0
    private var frame = 0
    private var walking = false
    private var facingRight = false
    private var x = -1f
    private var targetX = 0f
    private var actionEnds = 0L
    private var lastFrame = 0L
    private var lastTick = 0L
    private var running = false
    var laneLeft = 0f
    var laneRight = 0f

    fun start() { running = true; lastTick = SystemClock.uptimeMillis(); invalidate() }
    fun stop() { running = false }

    private fun pick(now: Long, petW: Float) {
        frame = 0
        val room = laneRight - laneLeft - petW
        walking = room > petW && Random.nextInt(100) < 45
        if (walking) {
            targetX = laneLeft + Random.nextFloat() * room
            if (abs(targetX - x) < room * 0.25f) targetX = if (x - laneLeft > room / 2) laneLeft else laneLeft + room
            facingRight = targetX > x
            row = if (facingRight) spec.walkRight else spec.walkLeft
            return
        }
        row = spec.actions.random()
        actionEnds = now + when (row) {
            spec.napRow -> 9000L
            0 -> 3000L + Random.nextLong(3000)
            else -> 2000L + Random.nextLong(1500)
        }
    }

    private fun frameMs() = when {
        walking -> 100L
        row == spec.napRow -> 300L
        row == 0 -> 170L
        else -> 130L
    }

    override fun onDraw(canvas: Canvas) {
        val sheet = sheet ?: return
        val now = SystemClock.uptimeMillis()
        val petH = height.toFloat()
        val petW = petH * cellW / cellH
        if (laneRight - laneLeft < petW) { if (running) postInvalidateDelayed(200); return }
        if (x < 0f) { x = laneLeft + Random.nextFloat() * (laneRight - laneLeft - petW); pick(now, petW) }
        x = x.coerceIn(laneLeft, laneRight - petW)
        val dt = (now - lastTick).coerceAtMost(100) / 1000f
        lastTick = now
        if (walking) {
            targetX = targetX.coerceIn(laneLeft, laneRight - petW)
            val step = 48 * d * dt
            if (abs(targetX - x) <= step) { x = targetX; pick(now, petW) }
            else x += if (targetX > x) step else -step
        } else if (now > actionEnds) pick(now, petW)
        val holding = row == spec.napRow && spec.napHold >= 0 && frame >= spec.napHold
        if (!holding && now - lastFrame > frameMs()) {
            lastFrame = now
            frame = if (row == spec.napRow && spec.napHold >= 0) minOf(frame + 1, spec.napHold) else (frame + 1) % spec.frames[row]
        }
        src.set(frame * cellW, row * cellH, (frame + 1) * cellW, (row + 1) * cellH)
        dst.set(x, 0f, x + petW, petH)
        if (walking && facingRight && spec.flipRight) {
            canvas.save()
            canvas.scale(-1f, 1f, dst.centerX(), dst.centerY())
            canvas.drawBitmap(sheet, src, dst, paint)
            canvas.restore()
        } else canvas.drawBitmap(sheet, src, dst, paint)
        if (running) postInvalidateDelayed(if (holding) 500 else 66)
    }
}
