#include <jni.h>
#include <string>
#include <android/log.h>
#include "module_manager.h"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "FeatherClient", __VA_ARGS__)

void registerFps(); void registerCps(); void registerKeystrokes(); void registerZoom(); void registerCoords();
void startHookThread(int64_t versionCode, const std::string& patternsPath);
std::string hookStatusString();
std::string scanPatternDebug(const std::string& pat);
bool nativeGetCoords(float out[3]);
void nativeSetZoomHeld(bool h);
void nativeSetZoomLevel(float f);

static void registerAll() {
    static bool done = false; if (done) return; done = true;
    registerFps(); registerCps(); registerKeystrokes(); registerZoom(); registerCoords();
}
static std::string str(JNIEnv* e, jstring s) { const char* c = e->GetStringUTFChars(s, nullptr); std::string r(c); e->ReleaseStringUTFChars(s, c); return r; }

JNIEXPORT jint JNI_OnLoad(JavaVM*, void*) { registerAll(); LOGI("clientmod loaded"); return JNI_VERSION_1_6; }

extern "C" {
JNIEXPORT jboolean JNICALL Java_com_example_bedrockclient_NativeBridge_init(JNIEnv* e, jobject, jstring cfg, jlong ver, jstring patterns) {
    registerAll();
    ModuleManager::get().applyConfig(str(e, cfg));
    static bool started = false;
    if (!started) { started = true; startHookThread((int64_t)ver, str(e, patterns)); }
    return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_com_example_bedrockclient_NativeBridge_setModule(JNIEnv* e, jobject, jstring id, jboolean on) { ModuleManager::get().setEnabled(str(e, id), on); }
JNIEXPORT jstring JNICALL Java_com_example_bedrockclient_NativeBridge_version(JNIEnv* e, jobject) { return e->NewStringUTF("1.6"); }
JNIEXPORT jstring JNICALL Java_com_example_bedrockclient_NativeBridge_hookStatus(JNIEnv* e, jobject) { return e->NewStringUTF(hookStatusString().c_str()); }
JNIEXPORT jstring JNICALL Java_com_example_bedrockclient_NativeBridge_scanPattern(JNIEnv* e, jobject, jstring p) { return e->NewStringUTF(scanPatternDebug(str(e, p)).c_str()); }
JNIEXPORT void JNICALL Java_com_example_bedrockclient_NativeBridge_setZoomHeld(JNIEnv*, jobject, jboolean h) { nativeSetZoomHeld(h); }
JNIEXPORT void JNICALL Java_com_example_bedrockclient_NativeBridge_setZoomLevel(JNIEnv*, jobject, jfloat f) { nativeSetZoomLevel(f); }
JNIEXPORT jfloatArray JNICALL Java_com_example_bedrockclient_NativeBridge_getCoords(JNIEnv* e, jobject) {
    float v[3]; if (!nativeGetCoords(v)) return nullptr;
    jfloatArray a = e->NewFloatArray(3); e->SetFloatArrayRegion(a, 0, 3, v); return a;
}
}
