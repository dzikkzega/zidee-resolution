Round 1 - Request Changes: Kanvas video 9:16 menyusut pada preset 720p
=====================================================================

**Phase 1 · T-1.3 · Terbuka.** `AGENTS.md:23-24` mewajibkan kanvas `1080×1920` untuk `BLUR_BG_9_16` dan `CROP_9_16`.

**Kasus:** Pilih video dan preset `HD_720P_ULTRA_SHARP`. Pada kedua mode tersebut, kalkulator menghasilkan `720×1280`, bukan `1080×1920`. Mode `ORIGINAL` tidak termasuk temuan.

**Bukti:** `domain/src/main/kotlin/com/zidee/ress/domain/usecase/CalculateOptimalSpecUseCase.kt:42-49` memakai kanvas yang telah diskalakan untuk blur dan crop. `domain/src/test/kotlin/com/zidee/ress/domain/usecase/CalculateOptimalSpecUseCaseTest.kt:184` justru mengharapkan `720×1280` untuk blur. Audit mencatat 71/71 tes core/domain lulus dan coverage baris domain 95,99%.

**Syarat penutupan:**
- [ ] Tes regresi memeriksa dimensi persis `1080×1920` untuk blur dan crop pada preset video 720p, tanpa mengubah perilaku sah `ORIGINAL`.
- [ ] Jalankan `./gradlew :core:build :domain:build :domain:koverXmlReport`; lampirkan hasil lint, tes, dan coverage baris domain minimal 90%.
