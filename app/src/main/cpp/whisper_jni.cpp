#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <string>
#include <thread>
#include <vector>

#include "whisper.h"

#define TAG "SobokWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_hafeziquran_app_data_whisper_WhisperBridge_initContext(
        JNIEnv * env, jobject /* thiz */, jstring model_path) {
    if (model_path == nullptr) return 0;

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) return 0;

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    struct whisper_context * ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGW("Failed to load model");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_hafeziquran_app_data_whisper_WhisperBridge_freeContext(
        JNIEnv * /* env */, jobject /* thiz */, jlong ctx_ptr) {
    if (ctx_ptr == 0) return;
    whisper_free(reinterpret_cast<struct whisper_context *>(ctx_ptr));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_hafeziquran_app_data_whisper_WhisperBridge_transcribe(
        JNIEnv * env, jobject /* thiz */, jlong ctx_ptr, jfloatArray samples, jstring language) {
    if (ctx_ptr == 0 || samples == nullptr) return nullptr;

    const jsize sample_count = env->GetArrayLength(samples);
    if (sample_count <= 0) return env->NewStringUTF("");

    std::vector<float> pcm(static_cast<size_t>(sample_count));
    env->GetFloatArrayRegion(samples, 0, sample_count, pcm.data());

    const char * lang = "ar";
    const char * held_lang = nullptr;
    if (language != nullptr) {
        held_lang = env->GetStringUTFChars(language, nullptr);
        if (held_lang != nullptr) lang = held_lang;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = false;
    params.suppress_blank = true;
    params.temperature = 0.0f;
    params.language = lang;
    params.n_threads = std::max(1, std::min(4, static_cast<int>(std::thread::hardware_concurrency())));

    const int result = whisper_full(
            reinterpret_cast<struct whisper_context *>(ctx_ptr),
            params, pcm.data(), static_cast<int>(pcm.size()));

    if (held_lang != nullptr) env->ReleaseStringUTFChars(language, held_lang);

    if (result != 0) {
        LOGW("whisper_full failed with code %d", result);
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(
            reinterpret_cast<struct whisper_context *>(ctx_ptr));
    for (int i = 0; i < segments; ++i) {
        const char * segment = whisper_full_get_segment_text(
                reinterpret_cast<struct whisper_context *>(ctx_ptr), i);
        if (segment == nullptr) continue;
        if (!text.empty()) text += ' ';
        text += segment;
    }

    return env->NewStringUTF(text.c_str());
}
