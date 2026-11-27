# CineMoen

Aplikasi Android (WebView) dengan layar pilihan alamat. Dibangun dalam dua varian dari kode yang sama:

| Varian | File APK | applicationId | Untuk |
|--------|----------|---------------|-------|
| mobile | `CineMoen.apk` | `com.cinemoen.app` | HP / tablet (layar sentuh) |
| tv | `CineMoen-TV.apk` | `com.cinemoen.app.tv` | Google TV / Android TV (remote) |

## Build APK lewat GitHub Actions
1. Push repo ini ke GitHub.
2. Buka tab **Actions** → workflow **Build APK** (jalan otomatis setiap push, atau klik **Run workflow**).
3. Setelah selesai, buka run tersebut. Di bagian *Artifacts* ada dua unduhan:
   - **CineMoen-apk** → berisi `CineMoen.apk` (HP)
   - **CineMoen-TV-apk** → berisi `CineMoen-TV.apk` (Google TV)
4. Ekstrak zip-nya, lalu install APK yang sesuai.

## Build lokal
Buka folder ini di Android Studio, pilih build variant `mobileDebug` atau `tvDebug`,
lalu **Build → Build APK(s)**. Atau lewat terminal: `gradle assembleMobileDebug assembleTvDebug`.
