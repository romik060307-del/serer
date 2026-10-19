package com.rkdesign.glasslauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class LauncherActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var islandButton: TextView
    private lateinit var widgetSubtext: TextView
    private lateinit var homeGrid: GridLayout
    private lateinit var pageIndicator: TextView
    private lateinit var dock: LinearLayout
    private lateinit var wallpaperImage: ImageView

    private var apps: List<LaunchableApp> = emptyList()
    private var homePackages = mutableListOf<String>()
    private var dockPackages = mutableListOf<String>()
    private var page = 0
    private var flashOn = false
    private var timer: CountDownTimer? = null
    private var timerEndsAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var gestureInProgress = false
    private var hasLoadedApps = false
    private var loadedWallpaperUri: String? = null
    private val clockHandler = android.os.Handler(mainLooper)
    private val clockUpdater = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(4, 17, 39)
        window.navigationBarColor = Color.rgb(4, 17, 39)
        window.decorView.systemUiVisibility = 0
        setContentView(R.layout.activity_launcher)

        root = findViewById(R.id.root)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        islandButton = findViewById(R.id.islandButton)
        widgetSubtext = findViewById(R.id.widgetSubtext)
        homeGrid = findViewById(R.id.homeGrid)
        pageIndicator = findViewById(R.id.pageIndicator)
        dock = findViewById(R.id.dock)
        wallpaperImage = findViewById(R.id.wallpaperImage)

        findViewById<View>(R.id.controlButton).setOnClickListener { openControlCenter() }
        findViewById<View>(R.id.settingsButton).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.notificationsButton).setOnClickListener { openNotificationCenter() }
        findViewById<View>(R.id.weatherButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Widget cuaca")
                .setMessage("Versi ini belum mengambil data cuaca dari internet. Home Screen tetap berfungsi offline dan tanpa pelacak.")
                .setPositiveButton("Mengerti", null)
                .show()
        }
        islandButton.setOnClickListener { showIslandDetails() }
        widgetSubtext.setOnClickListener { openLibrary(focusSearch = false) }
        findViewById<View>(R.id.homeIndicator).setOnClickListener { openLibrary(focusSearch = false) }

        applyPreferences()
        val savedTimer = LauncherStore.get(this).getLong("timer_ends_at", 0L)
        if (savedTimer > System.currentTimeMillis()) resumeTimer(savedTimer - System.currentTimeMillis())
        else LauncherStore.get(this).edit().remove("timer_ends_at").apply()
        updateClock()
        clockHandler.postDelayed(clockUpdater, 30_000L)
        restoreWallpaper()
        loadAppsAsync()
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        applyPreferences()
        restoreWallpaper()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockUpdater)
        timer?.cancel()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                gestureInProgress = false
                return super.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!gestureInProgress && (abs(dx) > dp(55) || abs(dy) > dp(65))) {
                    // Cancel the pressed icon as soon as a finger movement becomes a gesture.
                    gestureInProgress = true
                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
                if (gestureInProgress) return true
            }
            MotionEvent.ACTION_UP -> {
                if (gestureInProgress) {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    gestureInProgress = false
                    if (hasLoadedApps && abs(dx) > dp(95) && abs(dy) < dp(110)) {
                        val pageCount = maxOf(1, (homePackages.size + 15) / 16)
                        page = if (dx < 0) (page + 1).coerceAtMost(pageCount - 1) else (page - 1).coerceAtLeast(0)
                        renderHome()
                    } else if (hasLoadedApps && abs(dy) > dp(130) && abs(dx) < dp(110)) {
                        if (dy < 0) openLibrary(focusSearch = false) else openLibrary(focusSearch = true)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                gestureInProgress = false
                return super.dispatchTouchEvent(event)
            }
        }
        return if (gestureInProgress) true else super.dispatchTouchEvent(event)
    }

    private fun updateClock() {
        val now = Date()
        clockText.text = SimpleDateFormat("H:mm", Locale.getDefault()).format(now)
        dateText.text = SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID")).format(now)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("id", "ID")) else it.toString() }
        if (timerEndsAt > System.currentTimeMillis()) updateTimerLabel()
    }

    private fun loadAppsAsync() {
        widgetSubtext.text = "Memuat aplikasi…"
        object : AsyncTask<Void, Void, List<LaunchableApp>>() {
            override fun doInBackground(vararg params: Void?): List<LaunchableApp> = AppCatalog.load(this@LauncherActivity)
            override fun onPostExecute(result: List<LaunchableApp>) {
                apps = result
                val byPackage = apps.associateBy { it.packageName }
                homePackages = LauncherStore.readPackages(this@LauncherActivity, "home_apps")
                    .filter { byPackage.containsKey(it) }.toMutableList()
                if (homePackages.isEmpty()) {
                    homePackages = AppCatalog.prioritize(apps).take(24).map { it.packageName }.toMutableList()
                    LauncherStore.savePackages(this@LauncherActivity, "home_apps", homePackages)
                }
                dockPackages = LauncherStore.readPackages(this@LauncherActivity, "dock_apps")
                    .filter { byPackage.containsKey(it) }.toMutableList()
                if (dockPackages.isEmpty()) {
                    dockPackages = AppCatalog.prioritize(apps).take(4).map { it.packageName }.toMutableList()
                    LauncherStore.savePackages(this@LauncherActivity, "dock_apps", dockPackages)
                }
                hasLoadedApps = true
                widgetSubtext.text = "Geser ke atas untuk semua aplikasi"
                renderHome()
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    private fun renderHome() {
        if (!hasLoadedApps) return
        homeGrid.removeAllViews()
        val byPackage = apps.associateBy { it.packageName }
        val pages = maxOf(1, (homePackages.size + 15) / 16)
        page = page.coerceIn(0, pages - 1)
        val start = page * 16
        val current = homePackages.drop(start).take(16)
        for (i in 0 until 16) {
            val row = i / 4
            val column = i % 4
            val packageName = current.getOrNull(i)
            val tile: View = if (packageName != null && byPackage[packageName] != null) {
                val app = byPackage.getValue(packageName)
                makeAppTile(this, app, onClick = { launchApp(app.packageName) }, onLongClick = { showShortcutOptions(app) })
            } else {
                TextView(this).apply {
                    text = if (i == current.size && homePackages.size < 32) "+" else ""
                    textSize = 28f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setOnClickListener { openLibrary(focusSearch = false) }
                    contentDescription = "Tambahkan aplikasi ke Home Screen"
                }
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(column, 1f)
            ).apply {
                width = 0
                height = 0
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            homeGrid.addView(tile, params)
        }
        pageIndicator.text = (0 until pages).joinToString("   ") { if (it == page) "●" else "○" }
        pageIndicator.contentDescription = "Halaman Home ${page + 1} dari $pages; ketuk untuk pindah halaman"
        pageIndicator.setOnClickListener { if (pages > 1) { page = (page + 1) % pages; renderHome() } }
        renderDock(byPackage)
        applyTextColors(root, LauncherStore.get(this).getBoolean("theme_light", false))
    }

    private fun renderDock(byPackage: Map<String, LaunchableApp>) {
        dock.removeAllViews()
        dockPackages.take(4).forEach { pkg ->
            val app = byPackage[pkg] ?: return@forEach
            val tile = makeAppTile(this, app, labelVisible = false, iconSizeDp = 43, onClick = { launchApp(app.packageName) })
            dock.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        while (dock.childCount < 4) {
            dock.addView(makeGlassButton(this, "+") { openLibrary(false) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(3), dp(7), dp(3), dp(7))
            })
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Aplikasi tidak tersedia lagi", Toast.LENGTH_SHORT).show()
                loadAppsAsync()
            }
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aplikasi tidak dapat dibuka", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Akses aplikasi ditolak oleh Android", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showShortcutOptions(app: LaunchableApp) {
        val options = arrayOf("Buka aplikasi", "Lepas dari Home", "Info aplikasi", "Batal")
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> launchApp(app.packageName)
                    1 -> {
                        homePackages.remove(app.packageName)
                        LauncherStore.savePackages(this, "home_apps", homePackages)
                        page = page.coerceAtMost(maxOf(0, (homePackages.size - 1) / 16))
                        renderHome()
                    }
                    2 -> openAppInfo(app.packageName)
                }
            }
            .show()
    }

    private fun openAppInfo(packageName: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            Toast.makeText(this, "Info aplikasi tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLibrary(focusSearch: Boolean) {
        val intent = Intent(this, AppLibraryActivity::class.java)
        intent.putExtra("focus_search", focusSearch)
        startActivity(intent)
    }

    private fun restoreWallpaper() {
        val stored = LauncherStore.get(this).getString("wallpaper_uri", null)
        if (stored.isNullOrBlank()) {
            wallpaperImage.visibility = View.GONE
            loadedWallpaperUri = null
            return
        }
        if (loadedWallpaperUri == stored) return
        val uri = try { Uri.parse(stored) } catch (_: Exception) { return }
        loadedWallpaperUri = stored
        decodeWallpaperAsync(uri)
    }

    private fun applyPreferences() {
        if (!::root.isInitialized) return
        val prefs = LauncherStore.get(this)
        val lite = prefs.getBoolean("lite_mode", false)
        val light = prefs.getBoolean("theme_light", false)
        root.background = if (light) ColorDrawable(Color.rgb(224, 237, 250)) else getDrawable(R.drawable.bg_wallpaper)
        islandButton.visibility = if (prefs.getBoolean("show_island", true) && !lite) View.VISIBLE else View.INVISIBLE
        findViewById<View>(R.id.widgetCard).background = getDrawable(if (lite) R.drawable.bg_glass_small else R.drawable.bg_glass)
        applyTextColors(root, light)
    }

    private fun applyTextColors(view: View, light: Boolean) {
        if (view is TextView) {
            view.setTextColor(when {
                view.id == R.id.islandButton -> 0xFFC6E4FF.toInt()
                light -> 0xFF173653.toInt()
                else -> Color.WHITE
            })
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyTextColors(view.getChildAt(i), light)
        }
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { startActivityForResult(intent, REQUEST_WALLPAPER) }
        catch (_: Exception) { Toast.makeText(this, "Pemilih gambar tidak tersedia", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated by Android, kept for Android Oreo compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WALLPAPER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) { /* Some document providers do not grant persistable access. */ }
            LauncherStore.get(this).edit().putString("wallpaper_uri", uri.toString()).apply()
            loadedWallpaperUri = uri.toString()
            decodeWallpaperAsync(uri)
        }
    }

    private fun decodeWallpaperAsync(uri: Uri) {
        object : AsyncTask<Void, Void, Bitmap?>() {
            override fun doInBackground(vararg params: Void?): Bitmap? {
                return try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                    var sample = 1
                    while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2560) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                } catch (_: Exception) { null }
            }
            override fun onPostExecute(bitmap: Bitmap?) {
                if (bitmap == null) {
                    loadedWallpaperUri = null
                    Toast.makeText(this@LauncherActivity, "Wallpaper tidak dapat dibaca", Toast.LENGTH_SHORT).show()
                    return
                }
                wallpaperImage.setImageBitmap(bitmap)
                wallpaperImage.visibility = View.VISIBLE
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    private fun openControlCenter() {
        val dialog = Dialog(this)
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = getDrawable(R.drawable.bg_wallpaper)
        }
        panel.addView(makeLabel(this, "Control Center", 23f, Color.WHITE, true))
        panel.addView(makeLabel(this, "Pintasan sistem Android", 12f, 0xFFD0E5F7.toInt()).apply {
            setPadding(0, dp(3), 0, dp(14))
        })

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(makeControlTile("Wi-Fi", "Buka pengaturan Wi-Fi") { openSystemSettings(Settings.ACTION_WIFI_SETTINGS) }, weightParams())
        row1.addView(makeControlTile("Bluetooth", "Buka pengaturan Bluetooth") { openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }, weightParams())
        row1.addView(makeControlTile("Pesawat", "Pengaturan mode pesawat") { openSystemSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }, weightParams())
        panel.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row2.addView(makeControlTile("Senter", "Nyalakan atau matikan senter") { toggleFlashlight() }, weightParams())
        row2.addView(makeControlTile("Jangan Ganggu", "Izin akses khusus Android") { openSystemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }, weightParams())
        row2.addView(makeControlTile("Baterai", "Pengaturan baterai dan penghemat daya") { openSystemSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS) }, weightParams())
        panel.addView(row2)

        panel.addView(makeLabel(this, "Kecerahan layar", 14f, Color.WHITE, true).apply { setPadding(0, dp(10), 0, dp(4)) })
        val brightness = SeekBar(this).apply {
            max = 255
            progress = try { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (_: Exception) { 128 }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    if (!Settings.System.canWrite(this@LauncherActivity)) {
                        Toast.makeText(this@LauncherActivity, "Izinkan ubah pengaturan sistem untuk mengatur kecerahan", Toast.LENGTH_SHORT).show()
                        return
                    }
                    try {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, progress.coerceIn(1, 255))
                    } catch (_: Exception) { }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (!Settings.System.canWrite(this@LauncherActivity)) openSystemSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                }
            })
        }
        panel.addView(brightness, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

        panel.addView(makeLabel(this, "Volume media", 14f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, dp(3)) })
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volume = SeekBar(this).apply {
            max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        panel.addView(volume, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

        val currentMedia = LauncherNotificationListener.latestTitle
        val mediaText = if (currentMedia.isBlank()) "Belum ada judul media dari notifikasi yang diizinkan" else "Media terbaru: $currentMedia"
        panel.addView(makeLabel(this, mediaText, 12f, 0xFFD0E5F7.toInt()).apply { setPadding(0, dp(10), 0, dp(12)) })
        panel.addView(makeGlassButton(this, "Pilih wallpaper") { dialog.dismiss(); chooseWallpaper() })
        panel.addView(makeGlassButton(this, "Pengaturan launcher") { dialog.dismiss(); startActivity(Intent(this, SettingsActivity::class.java)) }.apply { setMarginsCompat(top = dp(8)) })
        panel.addView(makeGlassButton(this, "Tutup") { dialog.dismiss() }.apply { setMarginsCompat(top = dp(8)) })

        scroll.addView(panel)
        dialog.setContentView(scroll)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, minOf(resources.displayMetrics.heightPixels - dp(50), dp(680)))
    }

    private fun makeControlTile(title: String, description: String, action: () -> Unit): View {
        return TextView(this).apply {
            text = title
            contentDescription = description
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_glass_small)
            minHeight = dp(66)
            setPadding(dp(3), dp(8), dp(3), dp(8))
            setOnClickListener { animateTap(this); action() }
        }
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(68), 1f).apply {
        setMargins(dp(3), 0, dp(3), 0)
    }

    private fun View.setMarginsCompat(top: Int = 0) {
        val p = layoutParams
        if (p is LinearLayout.LayoutParams) {
            p.topMargin = top
            layoutParams = p
        } else {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top }
        }
    }

    private fun toggleFlashlight() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            return
        }
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (cameraId == null) {
                Toast.makeText(this, "Perangkat ini tidak memiliki senter yang didukung", Toast.LENGTH_SHORT).show()
                return
            }
            flashOn = !flashOn
            manager.setTorchMode(cameraId, flashOn)
        } catch (_: Exception) {
            Toast.makeText(this, "Senter tidak dapat diubah. Periksa izin/perangkat.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleFlashlight()
        else if (requestCode == REQUEST_CAMERA) Toast.makeText(this, "Izin kamera hanya digunakan untuk senter", Toast.LENGTH_SHORT).show()
    }

    private fun openSystemSettings(action: String, uri: Uri? = null) {
        try {
            val intent = if (uri != null) Intent(action, uri) else Intent(action)
            startActivity(intent)
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "Pengaturan Android tidak tersedia", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun openNotificationCenter() {
        if (!notificationAccessEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Akses notifikasi belum aktif")
                .setMessage("Android memerlukan persetujuan manual untuk menampilkan notifikasi di panel launcher. Isi notifikasi tidak dikirim ke server.")
                .setPositiveButton("Buka pengaturan") { _, _ -> openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                .setNegativeButton("Nanti", null)
                .show()
            return
        }
        val items = LauncherNotificationListener.snapshots.toList().sortedByDescending { it.whenMillis }
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = getDrawable(R.drawable.bg_wallpaper)
        }
        panel.addView(makeLabel(this, "Notification Center", 22f, Color.WHITE, true))
        panel.addView(makeLabel(this, "Notifikasi aktif di perangkat", 12f, 0xFFD0E5F7.toInt()).apply { setPadding(0, dp(3), 0, dp(12)) })
        if (items.isEmpty()) {
            panel.addView(makeLabel(this, "Tidak ada notifikasi aktif.", 14f).apply { setPadding(0, dp(12), 0, dp(20)) })
        } else {
            val scroll = ScrollView(this)
            val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            items.forEach { item ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = getDrawable(R.drawable.bg_glass_small)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                card.addView(makeLabel(this, item.title.ifBlank { item.packageName }, 14f, Color.WHITE, true))
                if (item.text.isNotBlank()) card.addView(makeLabel(this, item.text, 12f, 0xFFD0E5F7.toInt()))
                card.setOnClickListener {
                    try { item.contentIntent?.send() } catch (_: Exception) { launchApp(item.packageName) }
                    dialog.dismiss()
                }
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                list.addView(card, lp)
            }
            scroll.addView(list)
            panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)))
        }
        panel.addView(makeGlassButton(this, "Kelola akses notifikasi") { dialog.dismiss(); openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }.apply { setMarginsCompat(top = dp(10)) })
        panel.addView(makeGlassButton(this, "Tutup") { dialog.dismiss() }.apply { setMarginsCompat(top = dp(8)) })
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun notificationAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.contains(packageName)
    }

    private fun showIslandDetails() {
        val title = LauncherNotificationListener.latestTitle.ifBlank { "Tidak ada aktivitas media yang terdeteksi" }
        AlertDialog.Builder(this)
            .setTitle("RK Dynamic Island")
            .setMessage("$title\n\nDynamic Island ini adalah simulasi launcher Android. Informasi media dan notifikasi hanya muncul jika Android menyediakan datanya dan akses notifikasi sudah diizinkan.")
            .setPositiveButton(if (timerEndsAt > System.currentTimeMillis()) "Lihat timer" else "Mulai timer 1 menit") { _, _ ->
                if (timerEndsAt > System.currentTimeMillis()) updateTimerLabel() else startOneMinuteTimer()
            }
            .setNeutralButton("Pengaturan notifikasi") { _, _ -> openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun startOneMinuteTimer() = resumeTimer(60_000L)

    private fun resumeTimer(duration: Long) {
        timer?.cancel()
        timerEndsAt = System.currentTimeMillis() + duration
        LauncherStore.get(this).edit().putLong("timer_ends_at", timerEndsAt).apply()
        timer = object : CountDownTimer(duration, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                timerEndsAt = System.currentTimeMillis() + millisUntilFinished
                islandButton.text = "⏱  %02d:%02d".format(Locale.US, millisUntilFinished / 60_000, (millisUntilFinished / 1_000) % 60)
            }
            override fun onFinish() {
                timerEndsAt = 0L
                islandButton.text = "●  RK GLASS"
                LauncherStore.get(this@LauncherActivity).edit().remove("timer_ends_at").apply()
                Toast.makeText(this@LauncherActivity, "Timer selesai", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun updateTimerLabel() {
        val remain = (timerEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        islandButton.text = "⏱  %02d:%02d".format(Locale.US, remain / 60_000, (remain / 1_000) % 60)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQUEST_WALLPAPER = 41
        private const val REQUEST_CAMERA = 42
    }
}
