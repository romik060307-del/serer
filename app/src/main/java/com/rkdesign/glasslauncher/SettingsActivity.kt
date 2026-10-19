package com.rkdesign.glasslauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class SettingsActivity : Activity() {
    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherStore.get(this)
        window.statusBarColor = Color.rgb(4, 17, 39)
        window.navigationBarColor = Color.rgb(4, 17, 39)

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_wallpaper)
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(makeLabel(this, "Pengaturan", 28f, Color.WHITE, true))
        titles.addView(makeLabel(this, "RK Glass Launcher · Android 8+", 12f, 0xFFD0E5F7.toInt()).apply { setPadding(0, dp(3), 0, dp(14)) })
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(makeGlassButton(this, "Selesai") { finish() })
        outer.addView(header)

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(sectionTitle("Tampilan Home Screen"))
        body.addView(makeSwitch("Mode Lite", "Mengurangi efek tambahan untuk HP dengan RAM terbatas", "lite_mode", false))
        body.addView(makeSwitch("Animasi ringan", "Animasi sentuhan singkat sekitar 150 ms", "animations", true))
        body.addView(makeSwitch("Dynamic Island simulasi", "Menampilkan pil aktivitas di Home Screen", "show_island", true))
        body.addView(makeSwitch("Tema terang", "Mengubah warna dasar Home Screen", "theme_light", false))

        body.addView(sectionTitle("Personalisasi"))
        body.addView(buttonRow("Pilih wallpaper", "Ambil gambar dari perangkat") { chooseWallpaper() })
        body.addView(buttonRow("Atur aplikasi Home", "Cari aplikasi lalu tekan lama untuk menyematkan shortcut") {
            startActivity(Intent(this, AppLibraryActivity::class.java).putExtra("focus_search", false))
        })

        body.addView(sectionTitle("Izin dan fitur Android"))
        body.addView(buttonRow("Akses notifikasi", "Opsional, untuk Notification Center dan judul media") {
            openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        })
        body.addView(buttonRow("Izin kecerahan", "Opsional, untuk mengubah kecerahan sistem dari Control Center") {
            openSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
        })
        body.addView(buttonRow("Penjelasan izin", "Lihat fungsi dan alasan setiap izin") { showPermissions() })

        body.addView(sectionTitle("Cadangan lokal"))
        body.addView(buttonRow("Ekspor pengaturan", "Simpan salinan JSON ke perangkat") { startBackup() })
        body.addView(buttonRow("Pulihkan pengaturan", "Muat kembali berkas cadangan JSON") { startRestore() })
        body.addView(buttonRow("Reset pengaturan", "Hapus preferensi launcher dan atur ulang shortcut") { confirmReset() })

        body.addView(sectionTitle("Tentang aplikasi"))
        body.addView(makeLabel(this, "RK Glass Launcher adalah launcher Android dengan tampilan terinspirasi Liquid Glass. Ini bukan iOS asli dan tidak mengganti sistem keamanan Android.", 12f, 0xFFD0E5F7.toInt()).apply {
            setPadding(dp(3), dp(2), dp(3), dp(16))
        })
        scroll.addView(body)
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(outer)
    }

    private fun sectionTitle(title: String): TextView = makeLabel(this, title, 15f, 0xFF91CFFF.toInt(), true).apply {
        setPadding(dp(2), dp(17), dp(2), dp(8))
    }

    private fun makeSwitch(title: String, subtitle: String, key: String, defaultValue: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.bg_glass_small)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val description = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        description.addView(makeLabel(this, title, 14f, Color.WHITE, true))
        description.addView(makeLabel(this, subtitle, 11f, 0xFFD0E5F7.toInt()).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(description, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val switch = Switch(this).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
            contentDescription = title
        }
        row.addView(switch)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(6)
        row.layoutParams = lp
        return row
    }

    private fun buttonRow(title: String, subtitle: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_glass_small)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { animateTap(this); action() }
        }
        row.addView(makeLabel(this, title, 14f, Color.WHITE, true))
        row.addView(makeLabel(this, subtitle, 11f, 0xFFD0E5F7.toInt()).apply { setPadding(0, dp(3), 0, 0) })
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
        return row
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

    private fun startBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "rk-glass-settings-backup.json")
        }
        try { startActivityForResult(intent, REQUEST_BACKUP) }
        catch (_: Exception) { Toast.makeText(this, "Tidak ada aplikasi penyimpan berkas", Toast.LENGTH_SHORT).show() }
    }

    private fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivityForResult(intent, REQUEST_RESTORE) }
        catch (_: Exception) { Toast.makeText(this, "Tidak ada aplikasi pembuka berkas", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated by Android, retained for Android Oreo support")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_WALLPAPER -> {
                try {
                    val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: Exception) { }
                prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
                Toast.makeText(this, "Wallpaper dipilih. Terapkan saat kembali ke Home.", Toast.LENGTH_SHORT).show()
                finish()
            }
            REQUEST_BACKUP -> exportJson(uri)
            REQUEST_RESTORE -> importJson(uri)
        }
    }

    private fun exportJson(uri: Uri) {
        try {
            val json = JSONObject()
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is String, is Boolean, is Int, is Long, is Float, is Double -> json.put(key, value)
                    is Set<*> -> json.put(key, value.joinToString("|"))
                    else -> Unit
                }
            }
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json.toString(2)) }
                ?: throw IllegalStateException("Tidak dapat menulis file")
            Toast.makeText(this, "Cadangan pengaturan berhasil disimpan", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, "Gagal menyimpan cadangan", Toast.LENGTH_LONG).show() }
    }

    private fun importJson(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("File tidak dapat dibaca")
            val json = JSONObject(raw)
            val editor = prefs.edit()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = json.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                    JSONObject.NULL -> editor.remove(key)
                    else -> Unit
                }
            }
            editor.apply()
            Toast.makeText(this, "Cadangan dipulihkan. Kembali ke Home untuk menerapkan perubahan.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) { Toast.makeText(this, "Berkas cadangan tidak valid atau tidak bisa dibaca", Toast.LENGTH_LONG).show() }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset RK Glass Launcher?")
            .setMessage("Pengaturan, wallpaper tersimpan, dan daftar shortcut akan diatur ulang. Aplikasi Android yang terpasang tidak akan dihapus.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Reset") { _, _ ->
                prefs.edit().clear().apply()
                Toast.makeText(this, "Pengaturan direset", Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }

    private fun showPermissions() {
        AlertDialog.Builder(this)
            .setTitle("Izin RK Glass Launcher")
            .setMessage("• Kamera: hanya untuk menyalakan senter setelah persetujuan Android.\n\n• Akses notifikasi: opsional, untuk panel notifikasi dan judul media; isi notifikasi tidak dikirim ke server.\n\n• Ubah pengaturan sistem: opsional, untuk slider kecerahan.\n\n• Tidak perlu login atau koneksi internet untuk Home Screen dan App Library.")
            .setPositiveButton("Mengerti", null)
            .show()
    }

    private fun openSettings(action: String, uri: Uri? = null) {
        try { startActivity(if (uri == null) Intent(action) else Intent(action, uri)) }
        catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { } }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val REQUEST_WALLPAPER = 61
        private const val REQUEST_BACKUP = 62
        private const val REQUEST_RESTORE = 63
    }
}
