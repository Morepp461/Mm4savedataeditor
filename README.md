# MM4 Save Data Editor

Aplikasi Android untuk mengedit file career **Motorsport Manager 4 (MM4)**. Aplikasi ini membaca file save berformat `.msv` dengan container versi 40, menampilkan data dalam beberapa menu, lalu menyimpan hasil edit ke file baru. Sebelum file dibuat, aplikasi memeriksa ulang hasilnya agar data tidak berubah atau rusak saat proses ekspor.

## Menu yang tersedia

- **Overview:** ringkasan career dan pintasan ke menu lain.
- **Drivers:** daftar pembalap yang terhubung dengan data save. Bisa mengedit profil, atribut, potential/modifier, gaji, dan kontrak. Pemindahan pembalap ke tim lain tersedia melalui God Mode.
- **Staff:** data staf dan kandidat sesuai struktur yang ditemukan di save. Bisa mengedit profil, role, atribut, gaji, dan kontrak.
- **Teams:** nama tim, identitas singkat, reputasi, data keuangan utama, dan pilihan warna dari palet warna tim yang ditemukan di save.
- **Cars:** atribut performa mobil untuk masing-masing tim.
- **Shop:** stok komponen di Open Market dan toko supplier. Harga, diskon, rarity, status stok, tipe komponen, dan bonus statistik bisa diedit.
- **Finance:** saldo tim dan riwayat transaksi.
- **Supplier Network:** penjelajah 80 node jaringan (pemasok, manufaktur, dan perusahaan), cabang, koneksi jaringan, status kepemilikan, dan status ditemukan. Perubahan status menggunakan field boolean yang sudah ada pada node; struktur referensi dan koneksi tidak dibuat ulang.
- **Headquarters (HQ):** daftar 19 fasilitas beserta efeknya sesuai video gameplay yang diberikan, level kategori HQ, serta data upgrade node. Status, waktu unlock, dan tipe jalur upgrade bisa diedit dari data aslinya.
- **Component Marketplace:** daftar Open Market dan tujuh toko supplier: Micronix Racing, Shimizu, Polymat Group, Tectra Fibres, Norex, Mersault, dan Kitano. Nama komponen ditampilkan jika jenisnya sudah bisa dipastikan. ID yang belum diketahui tetap ditampilkan sebagai nomor, bukan ditebak.
- **World:** data championship, marketplace, supplier, research, sesi, sirkuit, dan cuaca yang ditemukan di save.\n- **God Mode:** Team Swap antar divisi, Move Driver, preset nama divisi F1–F4, dan preset lineup F1 2026. Preset F1 hanya diterapkan jika divisi berisi tepat 11 tim dengan dua pembalap per tim.
- **Save Info / Advanced:** pengeditan nilai dasar dengan tipe data yang didukung.
- **God Mode:** beberapa perubahan langsung pada data save.

## Fitur dan pengaman edit

- Kategori Supplier Network berdiri sendiri dan tidak mengubah menu lama. Data referensi ditampilkan sebagai label yang mudah dipahami, bukan metadata internal atau JSON mentah.
- Daftar node, cabang, dan koneksi dibaca secara hanya-baca. Hanya dua status boolean node yang dapat diubah melalui kontrol khusus.

- Menu Teams dan Staff punya batasan field masing-masing. Atribut mobil, pembalap, atau staf yang berada di bagian lain tidak akan ikut muncul sebagai data keuangan tim.
- Atribut mobil diedit lewat kartu atribut khusus.
- Ada **Undo dan Redo** untuk membatalkan atau mengulangi sampai 50 perubahan field terakhir.
- Aplikasi menjaga ketelitian angka desimal dan bilangan bulat yang besar saat diedit.
- Perpindahan antar-menu, kartu, dan intro aplikasi memakai animasi.\n- Intro menampilkan logo aplikasi, watermark Morepp di bagian akhir, dan panduan singkat saat pertama kali dibuka.\n- Batas atribut pembalap/staf dan batas HQ yang dapat dihitung dari node upgrade di save divalidasi sebelum disimpan.
- Watermark aplikasi: **Created by : Morepp**.

## Format file save

Codec saat ini mendukung format sampel MM4 dengan rincian berikut:

- Magic: `0x99131871`
- Versi container: `40`
- Enam nilai header Int32 little-endian (24 byte)
- Blok Save Info dan Game Data yang dikompresi dengan LZ4

File dengan magic atau versi yang tidak cocok, panjang data yang tidak valid, payload terpotong, atau ukuran file yang melewati batas akan ditolak.

Dukungan ini dibuat berdasarkan format file sampel yang sudah diperiksa. Jadi, belum bisa dijamin cocok dengan semua format save MM4 di masa mendatang.

## Impor dan ekspor save

- File dibuka melalui pemilih dokumen bawaan Android.
- Hasil edit disimpan lewat menu buat dokumen, ke lokasi file yang dipilih sendiri. File asli tidak ditimpa.
- Sebelum disimpan, file hasil edit dibaca ulang dan struktur JSON-nya dibandingkan dengan data yang akan diekspor.
- Field yang bernilai null dan field yang belum dikenal tetap dipertahankan.
- Kalau pemeriksaan gagal, aplikasi tidak akan meneruskan penyimpanan.

**Saran:** selalu simpan salinan file sebelum mengedit. Setelah ekspor, coba buka file hasilnya di Motorsport Manager 4 terlebih dahulu sebelum menjadikannya save utama.

Salah satu hasil ekspor dari build sebelumnya sudah pernah dibuka dan dimainkan di MM4. Pengujian tersebut berlaku untuk file dan build yang dicoba, bukan jaminan bahwa setiap jenis edit pasti aman untuk semua career.

## Build dan pemeriksaan otomatis

Setiap perubahan yang dikirim ke GitHub akan diperiksa melalui GitHub Actions. Prosesnya mencakup:

- Menjalankan unit test.
- Menjalankan Android lint.
- Membuat APK debug.
- Memeriksa identitas aplikasi di APK.
- Mengunggah APK sebagai artifact build.

APK untuk penggunaan pribadi ditandatangani dengan debug key dari proses build Android.

## Informasi versi

- Versi aplikasi: **1.6.0**
- Version code: **7**
- Application ID: `com.morepp.mm4savedataeditor`
- Minimum Android: **API 26**
- Target Android: **API 35**
- JDK 17, Gradle 8.9, Android SDK 35

Untuk rilis Play Store, aplikasi perlu ditandatangani dengan release keystore milik pemilik proyek. Jangan unggah file keystore atau password-nya ke repository.
