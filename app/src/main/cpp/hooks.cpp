#include "hook.h"
#include "module_manager.h"
#include <atomic>
#include <chrono>
#include <cmath>
#include <mutex>
#include <thread>
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "FeatherClient", __VA_ARGS__)

// ---- Modul (hanya penanda enable/disable; logikanya ada di hook di bawah) ----
class ZoomModule : public Module { public: ZoomModule() : Module("zoom") {} };
class CoordsModule : public Module { public: CoordsModule() : Module("coords") {} };
void registerZoom() { ModuleManager::get().registerModule(std::make_unique<ZoomModule>()); }
void registerCoords() { ModuleManager::get().registerModule(std::make_unique<CoordsModule>()); }

// ---- State ----
static std::atomic<bool> g_zoomHeld{false};
static std::atomic<float> g_zoomTarget{0.25f};
static std::atomic<void*> g_player{nullptr};
static std::atomic<uint32_t> g_tickCount{0};
static int g_posOffset = -1;
static float g_zoomFactor = 1.0f;
static std::mutex g_msgMu; static std::string g_msg = "waiting";
static void setMsg(const std::string& s) { std::lock_guard<std::mutex> l(g_msgMu); g_msg = s; LOG("hook status: %s", s.c_str()); }
std::string hookStatusString() { std::lock_guard<std::mutex> l(g_msgMu); return g_msg; }
void nativeSetZoomHeld(bool h) { g_zoomHeld = h; }
void nativeSetZoomLevel(float f) { g_zoomTarget = f < 0.05f ? 0.05f : (f > 1.0f ? 1.0f : f); }

// ---- Hook "fov": SIGNATURE HARUS COCOK dengan fungsi aslinya (default: float getFov(this, float, bool)) ----
static float (*orig_getFov)(void*, float, bool) = nullptr;
static float hooked_getFov(void* self, float a, bool b) {
    float fov = orig_getFov(self, a, b);
    if (!ModuleManager::get().isEnabled("zoom")) return fov;
    float target = g_zoomHeld.load() ? g_zoomTarget.load() : 1.0f;
    g_zoomFactor += (target - g_zoomFactor) * 0.2f;      // haluskan per panggilan
    return fov * g_zoomFactor;
}

// ---- Hook "player_tick": fungsi tick milik LocalPlayer (this = pemain lokal) ----
static void (*orig_tick)(void*, void*, void*, void*) = nullptr;
static void hooked_tick(void* self, void* a1, void* a2, void* a3) {
    g_player.store(self); g_tickCount.fetch_add(1);
    orig_tick(self, a1, a2, a3);
}

bool nativeGetCoords(float out[3]) {
    using namespace std::chrono;
    static uint32_t last = 0; static steady_clock::time_point lastChange = steady_clock::now();
    void* p = g_player.load();
    if (!p || g_posOffset < 0) return false;
    uint32_t c = g_tickCount.load(); auto now = steady_clock::now();
    if (c != last) { last = c; lastChange = now; }
    if (now - lastChange > seconds(1)) return false;     // tick berhenti: di menu / keluar world
    float v[3];
    if (!hook::safeRead((const char*)p + g_posOffset, v, sizeof v)) return false;
    for (int i = 0; i < 3; i++) if (!std::isfinite(v[i]) || std::fabs(v[i]) > 3e7f) return false;
    out[0] = v[0]; out[1] = v[1]; out[2] = v[2];
    return true;
}

std::string scanPatternDebug(const std::string& pat) {
    hook::Module m;
    if (!hook::findModule("libminecraftpe.so", m)) return "libminecraftpe belum ter-load (jalankan game dulu)";
    uintptr_t a = hook::scan(m, pat);
    if (!a) return "tidak ditemukan";
    char buf[64]; snprintf(buf, sizeof buf, "ketemu di vaddr 0x%lx", (unsigned long)(a - m.loadBase));
    return buf;
}

void startHookThread(int64_t versionCode, const std::string& patternsPath) {
    std::thread([versionCode, patternsPath] {
        std::string err;
        auto entries = hook::loadPatterns(patternsPath, err);
        if (entries.empty()) { setMsg(err == "no-file" ? "no-patterns-file" : "no-pattern"); return; }

        hook::Module m;
        for (int i = 0; i < 600 && !hook::findModule("libminecraftpe.so", m); i++)
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        if (!m.base) { setMsg("error:libminecraftpe tidak ter-load"); return; }

        std::string done;
        for (auto& e : entries) {
            if (versionCode < e.minVer || versionCode > e.maxVer) continue;
            uintptr_t addr = hook::scan(m, e.pattern);
            if (!addr) { LOG("pattern '%s' tidak ketemu", e.name.c_str()); continue; }
            addr += e.adjust;
            bool ok = false;
            if (e.name == "fov") ok = hook::install(addr, (void*)hooked_getFov, (void**)&orig_getFov);
            else if (e.name == "player_tick" && e.posOffset >= 0) { ok = hook::install(addr, (void*)hooked_tick, (void**)&orig_tick); if (ok) g_posOffset = e.posOffset; }
            if (ok) done += (done.empty() ? "" : ",") + e.name;
        }
        setMsg(done.empty() ? "no-pattern" : "ok:" + done);
    }).detach();
}
