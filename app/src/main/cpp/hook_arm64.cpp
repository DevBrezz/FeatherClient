#include "hook.h"
#include <sys/mman.h>
#include <unistd.h>
#include <cstring>
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "FeatherClient", __VA_ARGS__)

namespace hook {
#if defined(__aarch64__)
// Instruksi PC-relative tidak bisa disalin begitu saja ke trampoline -> ditolak dengan aman.
static bool pcRelative(uint32_t i) {
    return (i & 0x7C000000u) == 0x14000000u ||   // B / BL
           (i & 0x1F000000u) == 0x10000000u ||   // ADR / ADRP
           (i & 0x3B000000u) == 0x18000000u ||   // LDR (literal)
           (i & 0x7E000000u) == 0x34000000u ||   // CBZ / CBNZ
           (i & 0x7E000000u) == 0x36000000u ||   // TBZ / TBNZ
           (i & 0xFF000010u) == 0x54000000u;     // B.cond
}

bool installArm64(uintptr_t target, void* replacement, void** original) {
    uint32_t ins[4];
    memcpy(ins, (void*)target, 16);
    for (int i = 0; i < 4; i++) if (pcRelative(ins[i])) {
        LOG("hook ditolak: instruksi #%d (0x%08x) PC-relative. Geser pattern ke instruksi lain (adjust=) atau pakai Dobby.", i, ins[i]);
        return false;
    }
    void* tramp = mmap(nullptr, 4096, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (tramp == MAP_FAILED) { LOG("mmap RWX gagal"); return false; }
    auto* t = (uint32_t*)tramp;
    memcpy(t, ins, 16);                       // 4 instruksi asli
    t[4] = 0x58000051u;                       // ldr x17, #8
    t[5] = 0xD61F0220u;                       // br  x17
    uint64_t back = target + 16; memcpy(&t[6], &back, 8);
    __builtin___clear_cache((char*)tramp, (char*)tramp + 32);

    uint32_t patch[4] = {0x58000051u, 0xD61F0220u, 0, 0};
    uint64_t rep = (uint64_t)replacement; memcpy(&patch[2], &rep, 8);

    long pg = sysconf(_SC_PAGESIZE);
    uintptr_t start = target & ~(uintptr_t)(pg - 1);
    size_t len = ((target + 16 + pg - 1) & ~(uintptr_t)(pg - 1)) - start;
    if (mprotect((void*)start, len, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) { LOG("mprotect RWX gagal (SELinux?)"); return false; }
    memcpy((void*)target, patch, 16);
    __builtin___clear_cache((char*)target, (char*)target + 16);
    mprotect((void*)start, len, PROT_READ | PROT_EXEC);
    *original = tramp;
    return true;
}
#else
bool installArm64(uintptr_t, void*, void**) { LOG("backend bawaan hanya untuk arm64"); return false; }
#endif

bool safeRead(const void* p, void* out, size_t n) {
    int fd[2]; if (pipe(fd) != 0) return false;
    bool ok = write(fd[1], p, n) == (ssize_t)n && read(fd[0], out, n) == (ssize_t)n;   // write() -> EFAULT, bukan crash
    close(fd[0]); close(fd[1]);
    return ok;
}
}
