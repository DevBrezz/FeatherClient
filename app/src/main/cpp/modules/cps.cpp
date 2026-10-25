#include "module_manager.h"
#include <deque>
class CpsModule : public Module {
public:
    CpsModule() : Module("cps") {}
    void onInput(int key, bool down) override {
        if (key == 0 && down) clicks_.push_back(now());   // 0 = tap/klik kiri
    }
    void onRender(float) override {
        auto t = now();
        while (!clicks_.empty() && t - clicks_.front() > 1.0) clicks_.pop_front();
        cps = (int)clicks_.size();
    }
    int cps = 0;
private:
    static double now() { using namespace std::chrono; return duration<double>(steady_clock::now().time_since_epoch()).count(); }
    std::deque<double> clicks_;
};
void registerCps() { ModuleManager::get().registerModule(std::make_unique<CpsModule>()); }
