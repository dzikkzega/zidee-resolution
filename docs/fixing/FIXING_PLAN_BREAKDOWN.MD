Round 1 - Panduan pencatatan blocker QA/QC
==========================================

`fixing-plan/` menyimpan putusan QA per fase, bukan perubahan kode. Gunakan folder `phase 0` sampai `phase 6`. Folder kosong berarti belum ada blocker yang dicatat, bukan bukti kelulusan.

1. Gunakan satu berkas per fase per round: `round_RR_phase_N.md`, dengan nomor round dua digit dan nomor fase tanpa nol di depan. Contoh: `phase 1/round_01_phase_1.md` untuk Round 1 Phase 1.
2. Awali berkas dengan `Round N - Request Changes` atau `Round N - Verified`. Gabungkan semua temuan round itu sebagai subjudul; sertakan bukti dan syarat penutupan masing-masing.
3. Jika verifikasi ulang masih menemukan blocker, buat berkas blocker untuk round berikutnya. Jangan timpa riwayat.
4. Jika round lulus, buat `[nama round]_done.md` di folder fase yang sama. Gunakan `round_01_done.md` untuk Round 1; tidak perlu membuka Round 2 hanya untuk mencatat kelulusan. Awali dengan `Round N - Verified`. Tautkan berkas blocker, sebutkan temuan yang selesai, bukti kelulusan, toleransi atau pengecualian yang diterima (tulis *tidak ada* bila nihil), dan alasan QA menutup round.

## Sebelum mencatat putusan

Baca kriteria fase di [TASK_BREAKDOWN.md](../product/TASK_BREAKDOWN.md) dan riwayat dalam folder fase terkait. Catat perintah, hasil aktual, dan pemeriksaan yang belum dijalankan. Tes lokal yang lulus bukan bukti kelulusan di perangkat. Setelah mencatat putusan, perbarui indeks di bawah. Panduan disimpan di `docs/fixing/`; hanya laporan per fase yang disimpan di `fixing-plan/`.

## Round 1

- [Phase 0 — Aturan ECC dan dependensi unit test](../../fixing-plan/phase%200/round_01_phase_0.md): [Round 1 lulus](../../fixing-plan/phase%200/round_01_done.md).
- [Phase 1 — Kanvas video 9:16](../../fixing-plan/phase%201/round_01_phase_1.md): [Round 1 lulus](../../fixing-plan/phase%201/round_01_done.md).
- [Phase 2 — Kesesuaian Figcraft dan bukti penerimaan UI](../../fixing-plan/phase%202/round_01_phase_2.md): [Round 1 lulus](../../fixing-plan/phase%202/round_01_done.md).
- [Phase 3 — Memori crop foto dan verifikasi pipeline Android](../../fixing-plan/phase%203/round_01_phase_3.md): [Round 1 lulus](../../fixing-plan/phase%203/round_01_done.md).
- [Phase 4 — Publikasi MediaStore, izin Android 8–9, dan bukti Galeri](../../fixing-plan/phase%204/round_01_phase_4.md): [Round 1 lulus pada API 34](../../fixing-plan/phase%204/round_01_done.md); API 26–28 belum diuji pada perangkat.
- [Phase 5 — Pratinjau crop dan warna sumber](../../fixing-plan/phase%205/round_01_phase_5.md): [Round 1 lulus](../../fixing-plan/phase%205/round_01_done.md).
- [Phase 6 — Ktlint dan validasi MIME Quick Share](../../fixing-plan/phase%206/round_01_phase_6.md): [Round 1 lulus](../../fixing-plan/phase%206/round_01_done.md).
