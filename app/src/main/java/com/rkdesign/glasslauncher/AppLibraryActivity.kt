package com.rkdesign.glasslauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class AppLibraryActivity : Activity() {
    private lateinit var search: EditText
    private lateinit var grid: GridLayout
    private lateinit var emptyText: TextView
    private var allApps: List<LaunchableApp> = emptyList()
    private var homePackages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(4, 17, 39)
        window.navigationBarColor = Color.rgb(4, 17, 39)
        homePackages = LauncherStore.readPackages(this, "home_apps")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10))
            background = getDrawable(R.drawable.bg_wallpaper)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(makeLabel(this, "App Library", 27f, Color.WHITE, true))
        titleColumn.addView(makeLabel(this, "Semua aplikasi yang terpasang", 12f, 0xFFD0E5F7.toInt()).apply { setPadding(0, dp(2), 0, dp(10)) })
        top.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(makeGlassButton(this, "Selesai") { finish() })
        root.addView(top)

        search = EditText(this).apply {
            hint = "Cari aplikasi…"
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFFBDD4EB.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = getDrawable(R.drawable.bg_glass_small)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        emptyText = makeLabel(this, "Memuat aplikasi…", 13f, 0xFFD0E5F7.toInt()).apply {
            gravity = Gravity.CENTER
            visibility = View.VISIBLE
        }
        root.addView(emptyText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        val scroll = ScrollView(this)
        grid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = false }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(makeGlassButton(this, "Pengaturan launcher") { startActivity(Intent(this, SettingsActivity::class.java)) }.apply {
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46))
            p.topMargin = dp(8)
            layoutParams = p
        })
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        object : AsyncTask<Void, Void, List<LaunchableApp>>() {
            override fun doInBackground(vararg params: Void?): List<LaunchableApp> = AppCatalog.load(this@AppLibraryActivity)
            override fun onPostExecute(result: List<LaunchableApp>) {
                allApps = result
                emptyText.visibility = View.GONE
                renderApps(search.text.toString())
                if (intent.getBooleanExtra("focus_search", false)) {
                    search.requestFocus()
                    search.post {
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                            .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    override fun onResume() {
        super.onResume()
        homePackages = LauncherStore.readPackages(this, "home_apps")
    }

    private fun renderApps(query: String) {
        if (!::grid.isInitialized) return
        val q = query.trim().lowercase(Locale.ROOT)
        val filtered = if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase(Locale.ROOT).contains(q) }
        grid.removeAllViews()
        grid.rowCount = maxOf(1, (filtered.size + 3) / 4)
        filtered.forEachIndexed { index, app ->
            val tile = makeAppTile(this, app, onClick = { launchApp(app.packageName) }, onLongClick = { showAppOptions(app) })
            val p = GridLayout.LayoutParams(GridLayout.spec(index / 4, 1f), GridLayout.spec(index % 4, 1f)).apply {
                width = 0
                height = dp(94)
                setMargins(dp(1), dp(3), dp(1), dp(3))
            }
            grid.addView(tile, p)
        }
        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty()) emptyText.text = if (q.isBlank()) "Tidak ada aplikasi yang dapat dibuka." else "Aplikasi tidak ditemukan."
    }

    private fun showAppOptions(app: LaunchableApp) {
        val isPinned = homePackages.contains(app.packageName)
        val pinLabel = if (isPinned) "Lepas dari Home" else "Tambahkan ke Home"
        val options = arrayOf("Buka aplikasi", pinLabel, "Info aplikasi", "Batal")
        AlertDialog.Builder(this).setTitle(app.label).setItems(options) { _, which ->
            when (which) {
                0 -> launchApp(app.packageName)
                1 -> {
                    if (isPinned) homePackages.remove(app.packageName)
                    else if (homePackages.size < 32) homePackages.add(app.packageName)
                    else {
                        Toast.makeText(this, "Home Screen dibatasi 32 shortcut agar tetap ringan", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    LauncherStore.savePackages(this, "home_apps", homePackages)
                    Toast.makeText(this, if (isPinned) "Shortcut dilepas dari Home" else "Ditambahkan ke Home", Toast.LENGTH_SHORT).show()
                }
                2 -> try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                } catch (_: Exception) { Toast.makeText(this, "Info aplikasi tidak tersedia", Toast.LENGTH_SHORT).show() }
            }
        }.show()
    }

    private fun launchApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) startActivity(intent) else Toast.makeText(this, "Aplikasi tidak tersedia lagi", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { Toast.makeText(this, "Aplikasi tidak dapat dibuka", Toast.LENGTH_SHORT).show() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
