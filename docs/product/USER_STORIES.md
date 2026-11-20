# User Stories & Acceptance Criteria — Zidee Ress

Dokumen ini menjabarkan skenario penggunaan utama **Zidee Ress** dalam format *User Story* beserta kriteria penerimaan (*Acceptance Criteria*) berbasis Gherkin (`Given / When / Then`).

---

## Epic 1: Optimasi Cepat via Galeri HP (*Quick Share Bottom Sheet*)

### US-01: Optimasi & Kirim Langsung ke WA Story dari Galeri
**Sebagai** pengguna yang sedang melihat foto/video di Galeri HP,  
**Saya ingin** membagikan media ke Zidee Ress dan memilih target "WA Story" lewat *popup* cepat,  
**Agar** media saya otomatis dioptimalkan, tersimpan di album `ZideeResolution`, dan langsung diteruskan ke WhatsApp tanpa harus membuka aplikasi penuh.

#### Acceptance Criteria:
```gherkin
Scenario: Pengguna mengoptimalkan foto 4:3 dari Galeri untuk WA Story dengan Blur BG
  Given pengguna memilih sebuah foto beresolusi 4000x3000 di aplikasi Galeri HP
  When pengguna menekan tombol Share sistem Android dan memilih "Zidee Ress"
  Then panel Quick Share Bottom Sheet muncul di atas layar dalam waktu < 200ms
  And pengguna memilih target "WA Story" dan mode rasio "Blur BG 9:16"
  When pengguna menekan tombol "Simpan & Kirim"
  Then Zidee Ress memproses foto menjadi ukuran 1080x1920 dengan latar blur, Lanczos-3, dan Unsharp Mask
  And file hasil disimpan permanen ke folder Galeri "Pictures/ZideeResolution"
  And Zidee Ress membuka Share Intent langsung menuju aplikasi WhatsApp (com.whatsapp)
```

---

## Epic 2: Studio Editor & Pembanding *Split-Slider*

### US-02: Membandingkan Ketajaman Sebelum vs Sesudah dengan Garis Geser Kiri-Kanan
**Sebagai** pengguna yang peduli pada detail ketajaman foto,  
**Saya ingin** menggeser garis pembagi kiri-kanan (*Split Slider*) di layar Studio Editor,  
**Agar** saya bisa melihat langsung perbedaan ketajaman *Anti-Blur Sharpening* sebelum menyimpannya.

#### Acceptance Criteria:
```gherkin
Scenario: Menggeser garis Split Slider di Studio Editor
  Given pengguna membuka sebuah foto di layar Studio Editor Zidee Ress
  And pengaturan Anti-Blur Sharpening berada pada nilai 35%
  When pengguna menggeser garis vertikal Split Slider ke arah kiri atau kanan
  Then sisi kiri garis menampilkan gambar asli sebelum dioptimasi
  And sisi kanan garis menampilkan pratinjau gambar setelah Lanczos-3 dan Unsharp Mask
  And pergerakan garis berjalan mulus tanpa mengubah posisi zoom gambar
```

### US-03: Memilih Antara Rasio Asli, Blur BG 9:16, dan Crop 9:16 Interaktif
**Sebagai** pengguna yang ingin mengunggah foto/video ke IG Story atau WA Story,  
**Saya ingin** bisa memilih apakah media saya tetap pada rasio aslinya, diberi latar belakang *blur* `9:16`, atau dipotong (*crop*) ke `9:16`,  
**Agar** tampilan Story sesuai selera saya tanpa dirusak oleh pemotong bawaan WA/IG.

#### Acceptance Criteria:
```gherkin
Scenario: Mengubah mode kanvas di Studio Editor
  Given pengguna memuat foto landscape 16:9 ke dalam Studio Editor untuk target "IG Story"
  When pengguna memilih opsi "Blur BG 9:16"
  Then kanvas pratinjau berubah menjadi 9:16 (1080x1920) dengan foto utuh di tengah dan latar belakang blur
  When pengguna beralih ke opsi "Crop 9:16"
  Then pengguna dapat menggeser foto ke kiri/kanan untuk menentukan area potong 1080x1920
  When pengguna beralih ke opsi "Rasio Asli"
  Then kanvas kembali ke rasio 16:9 dengan lebar/sisi panjang terkunci di 1920px
```

---

## Epic 3: Kontrol Pro melalui Menu `⚙️ Advanced`

### US-04: Kustomisasi Parameter Video & Pemilihan Encoder (CPU vs GPU)
**Sebagai** pengguna tingkat lanjut (atau pemilik HP spek rendah/tinggi),  
**Saya ingin** membuka menu `⚙️ Advanced` untuk mengatur FPS, bitrate, resolusi (`1080p` vs `720p Ultra-Sharp`), konversi HDR ke SDR, dan mode mesin *encoder*,  
**Agar** saya bisa menyesuaikan keseimbangan antara kecepatan proses dan kualitas ketajaman maksimal.

#### Acceptance Criteria:
```gherkin
Scenario: Mengoptimalkan video 4K 60fps HDR menggunakan pengaturan Advanced
  Given pengguna memilih video 4K 60fps berformat HDR10 berdurasi 45 detik
  When pengguna membuka panel "⚙️ Advanced" pada target "WA Story"
  And pengguna mengaktifkan "Lock 30 FPS", "HDR ke SDR ON", dan "Auto-Split Per 30 Detik"
  And pengguna menekan tombol "Proses & Simpan"
  Then Foreground Service berjalan menampilkan progres persentase di notifikasi
  And Zidee Ress menghasilkan 2 file video (30 detik + 15 detik) beresolusi 1080x1920 30fps SDR
  And kedua file video tersimpan permanen di folder Galeri "Movies/ZideeResolution"
```

---

## Epic 4: Riwayat Galeri `ZideeResolution` & Kirim Ulang Instan

### US-05: Mengirim Ulang Media yang Sudah Dioptimasi ke Platform Lain Tanpa Render Ulang
**Sebagai** pengguna yang sudah mengoptimalkan video untuk WA Story,  
**Saya ingin** melihat daftar hasil optimasi di halaman utama Zidee Ress dan langsung membagikannya ke IG Story dengan 1 klik,  
**Agar** saya tidak perlu menunggu proses *render* ulang untuk file yang sama.

#### Acceptance Criteria:
```gherkin
Scenario: Re-share dari daftar riwayat ZideeResolution
  Given pengguna memiliki file video yang sebelumnya sudah dioptimasi di folder "ZideeResolution"
  When pengguna membuka halaman utama Zidee Ress
  Then file tersebut tampil di bagian "Tersimpan di ZideeResolution" lengkap dengan badge resolusi dan ukuran MB
  When pengguna menekan tombol "Share ke IG" pada kartu file tersebut
  Then aplikasi langsung membuka intent berbagi ke Instagram tanpa melakukan encoding ulang
```
