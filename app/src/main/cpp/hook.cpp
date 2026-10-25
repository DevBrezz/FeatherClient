#include "hook.h"
#include <link.h>
#include <cstring>
#include <sstream>
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "FeatherClient", __VA_ARGS__)
#ifdef USE_DOBBY
extern "C" int DobbyHook(void* address, void* replace, void** origin);
#endif

namespace hook {
struct Ctx { const char* name; Module* out; bool found; };

bool findModule(const char* soName, Module& out) {
    Ctx c{soName, &out, false};
    dl_iterate_phdr([](dl_phdr_info* info, size_t, void* d) -> int {
        auto* c = (Ctx*)d;
        if (!info->dlpi_name || !strstr(info->dlpi_name, c->name)) return 0;
        uintptr_t lo = UINTPTR_MAX, hi = 0;
        for (int i = 0; i < info->dlpi_phnum; i++) {
            auto& p = info->dlpi_phdr[i];
            if (p.p_type != PT_LOAD || !(p.p_flags & PF_X)) continue;   // hanya segmen executable
            lo = std::min<uintptr_t>(lo, info->dlpi_addr + p.p_vaddr);
            hi = std::max<uintptr_t>(hi, info->dlpi_addr + p.p_vaddr + p.p_memsz);
        }
        if (hi) { c->out->loadBase = info->dlpi_addr; c->out->base = lo; c->out->size = hi - lo; c->found = true; return 1; }
        return 0;
    }, &c);
    return c.found;
}

uintptr_t scan(const Module& m, const std::string& pattern) {
    std::vector<int> bytes; std::stringstream ss(pattern); std::string t;
    while (ss >> t) bytes.push_back(t == "??" || t == "?" ? -1 : (int)strtol(t.c_str(), nullptr, 16));
    if (bytes.empty() || m.size < bytes.size()) return 0;
    auto* p = (const uint8_t*)m.base;
    for (size_t i = 0; i + bytes.size() <= m.size; i++) {
        size_t j = 0;
        while (j < bytes.size() && (bytes[j] < 0 || p[i + j] == bytes[j])) j++;
        if (j == bytes.size()) return m.base + i;
    }
    return 0;
}

bool install(uintptr_t target, void* replacement, void** original) {
#ifdef USE_DOBBY
    return DobbyHook((void*)target, replacement, original) == 0;
#else
    return installArm64(target, replacement, original);
#endif
}
}
