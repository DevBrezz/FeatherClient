#include "module_manager.h"
#include <android/log.h>
class FpsModule : public Module {
public:
    FpsModule() : Module("fps") {}
    void onRender(float dt) override {
        acc_ += dt; frames_++;
        if (acc_ >= 1.f) { fps = frames_ / acc_; frames_ = 0; acc_ = 0; }
        // TODO: gambar teks "fps" lewat hook render game (lihat hooks/ di README).
    }
    float fps = 0;
private:
    float acc_ = 0; int frames_ = 0;
};
void registerFps() { ModuleManager::get().registerModule(std::make_unique<FpsModule>()); }
