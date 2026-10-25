#include "hook.h"
#include <fstream>
#include <sstream>

namespace hook {
std::vector<PatternEntry> loadPatterns(const std::string& path, std::string& err) {
    std::vector<PatternEntry> out;
    std::ifstream f(path);
    if (!f) { err = "no-file"; return out; }
    std::string line;
    while (std::getline(f, line)) {
        auto h = line.find('#'); if (h != std::string::npos) line.erase(h);
        auto bar = line.find('|'); if (bar == std::string::npos) continue;
        std::stringstream left(line.substr(0, bar));
        PatternEntry e; std::string tok;
        if (!(left >> e.minVer >> e.maxVer >> e.name)) continue;
        while (left >> tok) {
            auto eq = tok.find('='); if (eq == std::string::npos) continue;
            std::string k = tok.substr(0, eq), v = tok.substr(eq + 1);
            if (k == "adjust") e.adjust = (int)strtol(v.c_str(), nullptr, 0);
            else if (k == "pos_offset") e.posOffset = (int)strtol(v.c_str(), nullptr, 0);
        }
        e.pattern = line.substr(bar + 1);
        out.push_back(e);
    }
    if (out.empty()) err = "empty";
    return out;
}
}
