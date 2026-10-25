#include "module_manager.h"
#include <sstream>

ModuleManager& ModuleManager::get() { static ModuleManager m; return m; }
void ModuleManager::registerModule(std::unique_ptr<Module> m) { modules_.push_back(std::move(m)); }
void ModuleManager::setEnabled(const std::string& id, bool on) {
    for (auto& m : modules_) if (m->id() == id) m->enabled = on;
}
void ModuleManager::applyConfig(const std::string& cfg) {
    std::stringstream ss(cfg); std::string item;
    while (std::getline(ss, item, ';')) {
        auto eq = item.find('=');
        if (eq != std::string::npos) setEnabled(item.substr(0, eq), item.substr(eq + 1) == "1");
    }
}
void ModuleManager::tick() { for (auto& m : modules_) if (m->enabled) m->onTick(); }
void ModuleManager::render(float dt) { for (auto& m : modules_) if (m->enabled) m->onRender(dt); }
void ModuleManager::input(int k, bool d) { for (auto& m : modules_) if (m->enabled) m->onInput(k, d); }
bool ModuleManager::isEnabled(const std::string& id) const {
    for (auto& m : modules_) if (m->id() == id) return m->enabled;
    return false;
}
