# RK Glass Launcher

Launcher Android native bergaya Liquid Glass, terinspirasi dari tata letak iPhone pada gambar referensi. Aplikasi ini tetap launcher Android biasa, bukan iOS dan bukan pengganti keamanan sistem Android.

## Target teknis

- Kotlin + Android native Views/XML.
- Minimum Android 8.0 Oreo (API 26).
- Java 17, Android Gradle Plugin 8.6.1, Gradle 8.7, compile SDK 35.
- Tidak membutuhkan login, internet, server, iklan, atau dependency UI eksternal untuk fungsi inti.
- Ikon/nama aplikasi dibaca dari paket yang terpasang; pemindaian dilakukan di background dan cache ikon dibatasi.

## Fungsi yang tersedia dalam versi ini

- Launcher default Android dengan Home Screen, jam/tanggal, dock transparan, ikon aplikasi asli, dan halaman Home yang dapat digeser ke kiri/kanan.
- Buka aplikasi dengan satu sentuhan.
- App Library dengan pencarian instan; tekan lama sebuah aplikasi untuk menambah/melepas shortcut Home atau membuka Info Aplikasi.
- Wallpaper gambar pilihan dengan decoding bersampel agar tidak langsung memuat bitmap resolusi penuh.
- Mode Lite, animasi sentuhan ringan, tema terang, dan pilihan menampilkan Dynamic Island simulasi.
- Dynamic Island simulasi dengan timer satu menit dan informasi judul notifikasi terakhir jika izin notifikasi telah diberikan.
- Panel Control Center buatan aplikasi: membuka pengaturan Wi-Fi/Bluetooth/mode pesawat, senter dengan izin kamera, DND, baterai, slider kecerahan dengan akses khusus, dan volume media.
- Notification Center untuk menampilkan notifikasi aktif ketika pengguna secara manual mengaktifkan akses notifikasi Android.
- Ekspor/pemulihan preferensi melalui berkas JSON lokal.
- Workflow GitHub Actions di `.github/workflows/build-apk.yml` untuk membangun APK debug dan mengunggah hasilnya sebagai artifact.

## Batasan versi awal yang perlu diketahui

Versi pertama ini memprioritaskan launcher inti dan respons sentuhan. Belum semua butir dalam master prompt diwujudkan: folder drag-and-drop, perpindahan/penyusunan ulang ikon bebas, widget cuaca online, Calendar/Battery Widget lengkap, Android AppWidgetHost, pratinjau lock screen, kontrol transport musik penuh, dan penghapusan notifikasi langsung dari panel belum tersedia. Tombol cuaca menerangkan bahwa data cuaca belum tersambung. Kontrol Wi-Fi/Bluetooth/mode pesawat membuka pengaturan resmi Android alih-alih berpura-pura mengubah status sistem secara langsung.

Tidak ada klaim bahwa aplikasi sudah diuji pada Vivo Y71 atau bahwa APK berhasil dikompilasi di lingkungan penulisan ini. Runtime Android SDK dan Gradle tidak tersedia di lingkungan ini, maka gunakan workflow di bawah untuk melakukan build sesungguhnya.

## Membuat APK lewat GitHub Actions

1. Buat repository GitHub baru.
2. Unggah **isi folder proyek ini**, bukan ZIP sebagai satu file. Pastikan file tersembunyi ikut ada sehingga path workflow persis `.github/workflows/build-apk.yml`.
3. Setelah file masuk ke branch `main` atau `master`, buka tab **Actions**. Workflow bernama **Build RK Glass Launcher APK** akan berjalan otomatis. Workflow juga memiliki opsi **Run workflow**.
4. Buka proses build yang selesai dan unduh artifact **RK-Glass-Launcher-debug-apk**. File APK berada di dalam artifact tersebut sebagai `app-debug.apk`.
5. Pindahkan APK ke ponsel Android, instal, lalu tekan tombol Home. Pilih **RK Glass Launcher** sebagai aplikasi Home bawaan. Nama menu dapat berbeda menurut merek HP.

Jika build gagal, buka log langkah **Build debug APK** dan periksa baris error pertama. Jangan menganggap APK sudah berhasil dibuat sebelum workflow selesai dengan status hijau dan artifact APK tersedia.

## Izin Android

- **Kamera (opsional):** hanya untuk mengendalikan senter melalui API resmi Android. Ditanyakan saat fitur senter digunakan.
- **Notification access (opsional):** izin khusus yang pengguna aktifkan sendiri melalui Settings; diperlukan untuk membaca notifikasi aktif dan judul notifikasi di Dynamic Island. Isi notifikasi tidak dicatat ke log atau dikirim ke server.
- **Modify system settings (opsional):** izin khusus untuk mengubah kecerahan layar dari slider Control Center. Jika belum diizinkan, aplikasi membuka halaman izin Android.
- **Ubah volume media:** memakai API AudioManager Android.

## Uji manual sebelum dipakai sebagai launcher utama

- Instal di Android 8.0/8.1 dan pilih sebagai aplikasi Home.
- Buka beberapa ikon, kembali ke Home, buka App Library, dan coba pencarian.
- Tambahkan/lepas shortcut lalu tutup dan buka launcher lagi.
- Pilih wallpaper gambar yang besar dan cek penggunaan memori.
- Uji panel Control Center dengan izin diterima maupun ditolak.
- Uji Notification Center hanya setelah akses notifikasi diaktifkan manual.
- Uji Mode Lite, tema terang, rotasi layar, restart perangkat, serta perangkat dengan RAM terbatas.
- Jika muncul crash atau ANR, ambil logcat dari perangkat untuk dianalisis sebelum dipakai sebagai launcher default permanen.
