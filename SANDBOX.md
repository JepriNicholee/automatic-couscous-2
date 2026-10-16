# Mode sandbox (proot-distro)

Opt-in lewat Pengaturan > Lanjutan > "Mode sandbox". Default mati: perilaku lama tidak berubah.

## Yang dilakukan
- run_command berjalan di dalam proot-distro (default Debian), bukan di shell Termux utama.
- Workdir tamu: /root/sandbox/work (dibuat otomatis). Tanpa --bind, tanpa --shared-tmp, tanpa --termux-home,
  tanpa termux-setup-storage. Pemanggilan memakai --isolated.
- Environment dibersihkan: `env -i` hanya meneruskan PATH, HOME, TMPDIR, PREFIX, TERM, LANG, ANDROID_ROOT, ANDROID_DATA.
- Batas waktu (default 120 dtk, build 1800 dtk) lewat `timeout -s KILL` pada seluruh grup proses.
  Batas keluaran (default 20000 byte) lewat `head -c`; melewati batas diberi penanda "dipotong".
- CommandPolicy.assessCommand() (di atas classify(), classify() tidak diubah):
  SAFE (allowlist, baca-saja, di dalam workdir, tanpa jaringan), NEEDS_APPROVAL, BLOCKED.
- BLOCKED (tidak pernah dijalankan): dd, mkfs*, fdisk, mount, shred, su, proot*, termux-*, am/pm, reboot dst.;
  chmod/chown/chgrp -R; fork bomb; rm/unlink/truncate/find -delete atau penulisan (mkdir, cp, mv, tee, redirect, ...)
  ke luar workdir; rm workdir itu sendiri; cd keluar workdir.
- Jaringan (curl, wget, git clone/push/pull, pip, npm, apt, gradle, ssh, URL, /dev/tcp, ...) ditandai dan SELALU
  butuh persetujuan: tidak bisa dilewati mode otonom atau "Ya, semua" (force = true).
- write_file ditolak saat sandbox aktif (ia menulis ke Termux utama). Model diarahkan memakai run_command.
- Setiap eksekusi, penolakan, dan blokir dicatat di CommandLog (kolom baru: exitCode, decision, sandbox, network).

## Batas yang jujur
proot bukan sandbox keamanan seperti container/VM. Ia tidak memblokir jaringan dan tidak mencegah proses keluar
dari workdir di dalam sistem file tamu. Pembatasan workdir dan jaringan di sini adalah analisis teks (heuristik):
payload yang disandikan (mis. base64 | sh), variabel shell, atau skrip python/node bisa lolos dari analisis dan
hanya tertahan oleh dialog persetujuan serta isolasi proot.

## Pasang di Termux (sekali)
    pkg install -y proot-distro
    proot-distro install debian

## Cara mencoba di perangkat (tanpa perintah destruktif)
1. Cek dukungan opsi:  proot-distro login debian --help   (pastikan ada --isolated)
2. Cek isolasi:        proot-distro login debian --isolated -- ls /sdcard /data/data/com.termux/files/home
   Harus gagal atau kosong. Kalau isinya terlihat, JANGAN pakai mode ini.
3. Cek env bersih:     env -i PATH=$PREFIX/bin proot-distro login debian --isolated -- env
4. Nyalakan "Mode sandbox" di Pengaturan, lalu minta agen: "jalankan pwd dan ls" -> jalan, tanpa dialog bila autoread aktif.
5. "buat file a.txt berisi halo di workdir" -> dialog persetujuan; file muncul di /root/sandbox/work (di dalam Debian).
6. "jalankan sleep 300" -> berhenti di batas 120 dtk dengan pesan Timeout.
7. "tampilkan 100000 baris dengan yes | head -n 100000" -> keluaran dipotong dengan penanda.
8. "jalankan curl https://example.com" -> dialog dengan peringatan jaringan, tanpa tombol "Ya, semua".
9. Minta agen menjalankan `dd if=/dev/zero of=x bs=1 count=1` (hanya diuji untuk DIBLOKIR; tidak akan jalan) -> "Diblokir oleh sandbox".
10. Buka layar catatan perintah: entri baru punya kolom exit code, keputusan, sandbox, jaringan (tampilannya belum diubah).
