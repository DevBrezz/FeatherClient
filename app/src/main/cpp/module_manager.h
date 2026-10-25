#pragma once
#include "module.h"
#include <memory>
#include <vector>

class ModuleManager {
public:
    static ModuleManager& get();
    void registerModule(std::unique_ptr<Module> m);
    void applyConfig(const std::string& cfg);   // "fps=1;cps=0;..."
    void setEnabled(const std::string& id, bool on);
    bool isEnabled(const std::string& id) const;
    void tick();
    void render(float dt);
    void input(int key, bool down);
private:
    std::vector<std::unique_ptr<Module>> modules_;
};
