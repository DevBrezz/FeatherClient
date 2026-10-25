#include "module_manager.h"
class KeystrokesModule : public Module {
public:
    KeystrokesModule() : Module("keystrokes") {}
    void onInput(int key, bool down) override { if (key >= 0 && key < 8) pressed[key] = down; }
    bool pressed[8] = {};   // W A S D Jump Sneak LMB RMB
};
void registerKeystrokes() { ModuleManager::get().registerModule(std::make_unique<KeystrokesModule>()); }
