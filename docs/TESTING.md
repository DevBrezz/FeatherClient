# Verifikasi dependensi Minecraft Bedrock (butuh perangkat/emulator arm64)

| # | Skenario | Cara menguji | Hasil yang diharapkan |
|---|----------|--------------|-----------------------|
| 1 | Terinstall & kompatibel | Install Minecraft (Play Store), buka Feather | Home normal, tombol Launch aktif |
| 2 | Tidak terinstall | `adb uninstall com.mojang.minecraftpe`, buka Feather | Layar "Minecraft Bedrock Edition diperlukan" |
| 3 | Diinstall setelah Feather terbuka | Biarkan layar #2, install Minecraft, kembali ke Feather (atau tekan Coba Lagi) | Pindah ke home; Launch berhasil tanpa restart |
| 4 | Terinstall tapi tidak kompatibel | `adb shell pm disable-user --user 0 com.mojang.minecraftpe` | "Tidak kompatibel" + pemeriksaan ✗ "Aplikasi aktif" + tombol info aplikasi |
| 5 | Error sistem | Sulit disimulasikan; jalur `MinecraftStatus.Error` dicakup try/catch Throwable | Layar "Gagal memeriksa" + Coba Lagi, tanpa crash |
| 6 | Gagal diluncurkan | Matikan Minecraft lalu tekan Launch dari layar lama; atau hapus `libminecraftpe.so` dari split | Toast pesan jelas; bila game crash saat start, dialog "Peluncuran sebelumnya tidak selesai" di buka berikutnya |

Log: `adb logcat -s FeatherClient`. Diagnostik: tombol "Salin info diagnostik".
