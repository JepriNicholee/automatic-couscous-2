# Perubahan tampilan v2.2 (modern, sederhana, tajam)

Tujuan: layar lebih bersih, satu cara untuk tiap hal, mudah dioperasikan satu tangan.
Logika agen, penyedia model, dan keamanan tidak diubah.

## Chat
- Latar putih bersih (dulu gradasi biru berawan). Teks AI 16,5sp, spasi baris 1,35, warna tinta tunggal.
- Hapus kilau biru berjalan pada teks yang sedang mengalir, dan hapus kata tebal/kode yang berwarna biru.
  Tebal = tebal biasa; kode inline = abu lembut.
- Hapus header "AI Agent - status" di tiap balasan (mengulang baris aktivitas). Status hanya di SATU pil abu di atas jawaban.
- Pil status tanpa awalan "AI Agent"; hasil akhir ringkas: "Berhasil - n langkah".
- Gelembung pengguna lebih rapat; chip saran putih bergaris halus (tidak lagi biru).

## Header dan input
- Header: pil model di kiri, chat baru + pengaturan (ikon roda gigi) di kanan. Ikon menu tiga garis (yang dulu membuka Pengaturan) dan avatar dihapus.
- Input: satu tombol aksi saja (mikrofon saat kosong, panah kirim saat ada teks, kotak saat berjalan). Tombol mikrofon ganda dihapus.
- Layar kosong kini punya sapaan dan tiga contoh perintah (mengisi kolom input, tidak langsung menjalankan).

## Pengaturan, model, izin
- Pengaturan jadi lembar bawah: Penyedia AI, API key, Simpan. Menu pendukung berupa baris tautan. Opsi lanjutan memakai saklar dengan keterangan satu baris.
- Pemilih model jadi lembar bawah dengan daftar bersih, centang pada model aktif, penanda "sibuk".
- Dialog izin: penjelasan, label risiko berwarna lembut, perintah di kotak abu. Tombol "Jalankan", "Tolak", "Ya, semua".
- Dialog sistem: sudut 28dp, tombol tanpa huruf kapital semua.

## File
- Baru: Ui.kt (pembantu bentuk, tombol, lembar bawah).
- Diubah: Palette, MainActivity, ChatMessages, Markdown, MessageMenu, ActivityZone, AgentStepList, StatusBar,
  SettingsDialog, ModelPicker, ApprovalDialog, ChatControls, ModelCall/ToolExecution (hanya teks tombol), styles/colors/dialog_bg.
- Belum dikompilasi di lingkungan penyusunan: jalankan WITH_TESTS=1 bash build-termux.sh.

## AI bekerja mandiri (v2.3)
- Prompt sistem ditulis ulang: dari sekitar 30 aturan kaku menjadi satu paragraf pendek. Isinya hanya fakta lingkungan
  (Termux, folder kerja, alat, folder lampiran) dan kebiasaan yang meningkatkan mutu kerja (pahami tujuan, baca dulu,
  belajar dari kegagalan, periksa hasil, lapor jujur). Keputusan bertindak atau menjawab ada di AI.
- Dihapus: pesan dorongan tersembunyi "Kamu belum menjalankan langkah apa pun..." (AgentLoop).
- Tahap "Memahami permintaan" kini mati secara bawaan (pengaturan "understand" = false); hemat satu panggilan model per pesan.
- Tetap ada: dialog persetujuan, mode hanya baca/sandbox/otonom, pengingat verifikasi build (maks 2 kali).

## Baris status live + terminal live (v2.5)
Tujuan: satu baris status yang bersih seperti di aplikasi Claude, dan kode/perintah AI bisa dilihat langsung saat diketuk.

- Pil abu + daftar langkah lama diganti `LiveStatusLine` (LiveStatusLine.kt): ikon, teks berkilau, panah.
  - Ikon berganti sesuai kerja AI: titik oranye mengorbit saat berpikir; saat menjalankan langkah ikon mengikuti jenisnya
    (baca, cari, tulis, build = roda gigi berputar, arsip, unduh, uji, ...). Ikon baru muncul dengan efek membal.
  - Teks berkilau abu-ke-gelap, naik-memudar saat berganti, dipotong "..." sebelum panah. Tahan 0,7 detik agar tidak berkedip (TextGate).
  - Teks langkah kini memakai kalimat `deskripsi` yang ditulis AI sendiri (mis. "Memeriksa isi arsip ZIP"), bukan judul generik.
  - Saat selesai: ikon centang hijau / silang merah / dll, teks diam ("Berhasil - 3 langkah"). Panah tetap bisa dibuka untuk meninjau.
- Ketuk baris -> terminal gelap terbuka dengan animasi tinggi + pudar (`LiveTerminal`):
  - Perintah diberi prompt `$` dan diwarnai (perintah cyan, opsi oranye, string hijau, operator ungu, variabel kuning).
  - Keluaran mengalir baris demi baris (efek ketik) dan diwarnai: `> Task` redup, `BUILD SUCCESSFUL` hijau, error merah, warning kuning,
    angka oranye, path ungu. Kursor hijau berkedip selama perintah jalan.
  - Tulis file: path + isi bernomor dengan warna kode; setelah selesai tampil diff (+ hijau, - merah).
  - Deretan chip langkah (geser samping): ketuk untuk melihat langkah lain; ketuk chip terakhir untuk kembali mengikuti live.
  - Tombol Salin (salin isi terminal), Besar/Kecil (tinggi 250 / 460 dp), penghitung waktu berjalan, tombol Setuju/Tolak saat menunggu izin.
  - Tekan lama judul terminal -> panel komputer lengkap (seperti sebelumnya).
  - Gulir otomatis mengikuti keluaran terbaru; bila pengguna menggulir ke atas, tidak ditarik turun.
- Pembacaan keluaran live (`LiveTail`) dipercepat dari 2,5 dtk ke 1,5 dtk (build tetap 5 dtk).
- Baru: LiveStatusLine.kt (tampilan), TerminalLex.kt (pewarnaan murni, teruji), test TerminalLexTest.kt.
- Diubah: ActivityZone.kt (memakai LiveStatusLine), AgentLoop.kt (`stepTitleFor`), LiveTail.kt (interval).
- Belum dikompilasi di lingkungan penyusunan: jalankan WITH_TESTS=1 bash build-termux.sh.
