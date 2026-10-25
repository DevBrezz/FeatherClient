#pragma once
#include <cstdint>
#include <string>
#include <vector>

namespace hook {
struct Module { uintptr_t base = 0; size_t size = 0; uintptr_t loadBase = 0; };   // base/size = segmen executable

bool findModule(const char* soName, Module& out);
// Pattern scan "48 8B ?? 05" (?? = wildcard) di segmen executable. Return 0 jika tidak ketemu.
uintptr_t scan(const Module& m, const std::string& pattern);
// Inline hook. Backend: Dobby (-DUSE_DOBBY) atau backend arm64 bawaan (hook_arm64.cpp).
bool install(uintptr_t target, void* replacement, void** original);
bool installArm64(uintptr_t target, void* replacement, void** original);
// Baca memori dengan aman (return false, bukan crash, jika alamat tidak valid).
bool safeRead(const void* p, void* out, size_t n);

struct PatternEntry {
    int64_t minVer = 0, maxVer = INT64_MAX;
    std::string name, pattern;
    int adjust = 0;        // geser alamat hasil scan (byte)
    int posOffset = -1;    // khusus "player_tick": offset Vec3 posisi dari 'this'
};
// Format patterns.txt: "<minVer> <maxVer> <name> [adjust=N] [pos_offset=0xN] | <pattern>"
std::vector<PatternEntry> loadPatterns(const std::string& path, std::string& err);
}
