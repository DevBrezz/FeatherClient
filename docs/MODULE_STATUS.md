# Status modul Feather Client

Dihasilkan otomatis dari `ModuleRegistry.kt` (`python3 tools/validate_registry.py --status`).

Status: **NOT TESTED** = kode ada, belum diuji di Minecraft/perangkat. **BLOCKED** = tidak bisa jalan sampai prasyarat terpenuhi. Tidak ada modul berstatus WORKING karena belum ada pengujian runtime.

| Modul | Kategori | Status | Catatan |
|---|---|---|---|
| Armor Status | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Block Coordinates | HUD | BLOCKED | Butuh hook native (player_tick) + patterns.txt per versi; kode sudah ada, NOT TESTED |
| Boss Bar | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Clock | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Combo Count | HUD, Combat | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Compass | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Coordinates | HUD | BLOCKED | Butuh hook native (player_tick) + patterns.txt per versi; kode sudah ada, NOT TESTED |
| CPS | HUD, Combat | NOT TESTED | Diimplementasikan (data lokal Android) |
| Day Counter | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Entity Count | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| FPS | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Info HUD | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Keystrokes | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Memory | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Minimap | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Potion Effects | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Ping | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Session Timer | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Speed Display | HUD | BLOCKED | Butuh hook native (player_tick) + patterns.txt per versi; kode sudah ada, NOT TESTED |
| Direction Indicator | HUD | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Module List | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Battery | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Network | HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Hit Indicator | Combat | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Attack Information | Combat | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Target Information | Combat | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Game Information | Gamemode | NOT TESTED | Diimplementasikan (data lokal Android) |
| World Information | Gamemode | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Player Information | Gamemode | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Server Information | Gamemode | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Current Dimension | Gamemode | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Toggle Sprint | Mechanic, Movement | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Toggle Sneak | Mechanic, Movement | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Zoom | Mechanic, Visual | BLOCKED | Butuh hook native (fov) + patterns.txt per versi; kode sudah ada, NOT TESTED |
| Perspective Shortcut | Mechanic | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Touch Control Customization | Mechanic | BLOCKED | Belum didukung: butuh data/integrasi game yang tidak ada |
| Custom Crosshair | Visual | NOT TESTED | Diimplementasikan (data lokal Android) |
| HUD Visibility | Visual | NOT TESTED | Panel/alat |
| HUD Scale | Visual | NOT TESTED | Panel/alat |
| HUD Color | Visual | NOT TESTED | Panel/alat |
| Notifications | Utility | NOT TESTED | Diimplementasikan (data lokal Android) |
| Configuration Manager | Utility | NOT TESTED | Panel/alat |
| Performance Information | Utility, HUD | NOT TESTED | Diimplementasikan (data lokal Android) |
| Compatibility Diagnostics | Utility | NOT TESTED | Panel/alat |
| Reset Settings | Utility | NOT TESTED | Panel/alat |
| Debug Information | Utility | NOT TESTED | Panel/alat |
| Stopwatch | Utility | NOT TESTED | Panel/alat |
| Countdown | Utility | NOT TESTED | Panel/alat |
| Screenshot | Utility | NOT TESTED | Panel/alat |
| Quick Notes | Utility | NOT TESTED | Panel/alat |
