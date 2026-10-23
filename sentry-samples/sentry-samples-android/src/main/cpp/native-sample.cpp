#include <jni.h>
#include <android/log.h>
#include <sentry.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/resource.h>
#include <unistd.h>

#define TAG "sentry-sample"

extern "C" {

static char crash_message[512] = "status=fault scenario=baseline run_id=manual";

static void log_crash() {
    __android_log_write(ANDROID_LOG_WARN, "SentryCrashScenario", crash_message);
}

static void scenario_failed(const char *message, int error = 0) {
    __android_log_print(ANDROID_LOG_ERROR, "SentryCrashScenario",
                        "status=error %s: %s (%d)",
                        crash_message + sizeof("status=fault ") - 1, message, error);
}

// Faults inside this named function so the crashing frame resolves to a real
// symbol + source line. A bare raise(SIGSEGV) would instead fault in libc and,
// for a JNI-originated crash, not exercise app-native symbolication.
[[gnu::noinline]]
static void trigger_null_deref() {
    volatile int *ptr = nullptr;
    *ptr = 42;
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crash(JNIEnv *env, jclass cls) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "About to crash.");
    log_crash();
    trigger_null_deref();
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_message(JNIEnv *env, jclass cls) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "Sending message.");
    sentry_value_t event = sentry_value_new_message_event(
            /*   level */ SENTRY_LEVEL_INFO,
            /*  logger */ "custom",
            /* message */ "Native Capture button: native message"
    );
    sentry_capture_event(event);
}

[[gnu::noinline]]
static void idle_pointlessly() {
    static const volatile int x = 42;
    (void)x;
}

[[gnu::noinline]]
static void loop_eternally() {
    while (true) {
        idle_pointlessly();
    }
}

[[gnu::noinline]]
static void keep_object_locked(JNIEnv* env, jobject obj) {
    env->MonitorEnter(obj);
    loop_eternally();
    env->MonitorExit(obj);
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_freezeMysteriously(JNIEnv *env, jclass cls, jobject obj) {
    __android_log_print(ANDROID_LOG_WARN, TAG, "About to lock object eternally.");
    keep_object_locked(env, obj);
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_prepareCrash(JNIEnv *env, jclass cls, jstring scenario, jstring run_id) {
    const char *scenario_chars = env->GetStringUTFChars(scenario, nullptr);
    if (scenario_chars == nullptr) {
        return;
    }
    const char *run_chars = env->GetStringUTFChars(run_id, nullptr);
    if (run_chars != nullptr) {
        snprintf(crash_message, sizeof(crash_message), "status=fault scenario=%s run_id=%s",
                 scenario_chars, run_chars);
        env->ReleaseStringUTFChars(run_id, run_chars);
    }
    env->ReleaseStringUTFChars(scenario, scenario_chars);
    // Warm up liblog before exhausting the heap or descriptors.
    __android_log_print(ANDROID_LOG_WARN, "SentryCrashScenario", "status=ready %s",
                        crash_message + sizeof("status=fault ") - 1);
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashOutOfMemory(JNIEnv *env, jclass cls) {
    FILE *statm = fopen("/proc/self/statm", "r");
    if (statm == nullptr) {
        scenario_failed("open statm", errno);
        return;
    }
    unsigned long pages = 0;
    int fields = fscanf(statm, "%lu", &pages);
    fclose(statm);
    struct rlimit limit;
    if (fields != 1 || getrlimit(RLIMIT_AS, &limit) != 0) {
        scenario_failed("read address space limit", errno);
        return;
    }
    rlim_t target = static_cast<rlim_t>(pages) * sysconf(_SC_PAGESIZE) + 4 * 1024 * 1024;
    limit.rlim_cur = target < limit.rlim_max ? target : limit.rlim_max;
    if (setrlimit(RLIMIT_AS, &limit) != 0) {
        scenario_failed("set address space limit", errno);
        return;
    }
    static void *volatile allocation;
    do {
        allocation = malloc(1024 * 1024);
    } while (allocation != nullptr);
    log_crash();
    trigger_null_deref();
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashLockedHeap(JNIEnv *env, jclass cls) {
    auto disable = reinterpret_cast<void (*)()>(dlsym(RTLD_DEFAULT, "malloc_disable"));
    if (disable == nullptr) {
        scenario_failed("malloc_disable is unavailable");
        return;
    }
    disable();
    log_crash();
    trigger_null_deref();
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashCorruptedHeap(JNIEnv *env, jclass cls) {
    static void *blocks[8192];
    for (size_t i = 0; i < sizeof(blocks) / sizeof(blocks[0]); ++i) {
        blocks[i] = malloc(1024);
        if (blocks[i] == nullptr) {
            scenario_failed("allocate heap buffers", errno);
            return;
        }
    }
    FILE *maps = fopen("/proc/self/maps", "r");
    if (maps == nullptr) {
        scenario_failed("open maps", errno);
        return;
    }
    const size_t size = 4 * 1024 * 1024;
    uintptr_t address = 0;
    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        unsigned long start, stop;
        char permissions[5];
        if (sscanf(line, "%lx-%lx %4s", &start, &stop, permissions) != 3 ||
            permissions[1] != 'w' || stop - start < size) {
            continue;
        }
        for (void *block : blocks) {
            uintptr_t candidate = reinterpret_cast<uintptr_t>(block);
#if defined(__aarch64__)
            candidate &= 0x00ffffffffffffffULL;
#endif
            if (candidate >= start && candidate <= stop - size) {
                address = candidate;
                break;
            }
        }
        if (address != 0) {
            break;
        }
    }
    fclose(maps);
    if (address == 0) {
        scenario_failed("heap mapping is too small for a 4 MiB overflow");
        return;
    }
    // Stay inside the mapping so the overflow damages chunk headers before the fault.
    volatile unsigned char *buffer = reinterpret_cast<volatile unsigned char *>(address);
    for (size_t i = 0; i < size; ++i) {
        buffer[i] = 0xa5;
    }
    log_crash();
    trigger_null_deref();
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashNoFileDescriptors(JNIEnv *env, jclass cls) {
    while (open("/dev/null", O_RDONLY | O_CLOEXEC) >= 0) {}
    if (errno != EMFILE) {
        scenario_failed("exhaust file descriptors", errno);
        return;
    }
    log_crash();
    trigger_null_deref();
}

[[gnu::noinline]]
static void corrupt_stack() {
    // Replace saved frame pointers and return addresses, including this frame's.
    volatile uintptr_t *frame = static_cast<volatile uintptr_t *>(__builtin_frame_address(0));
    for (size_t i = 0; i < 16; ++i) {
        frame[i] = static_cast<uintptr_t>(0xa5a5a5a5a5a5a5a5ULL);
    }
    log_crash();
    trigger_null_deref();
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashStackCorruption(JNIEnv *env, jclass cls) {
    corrupt_stack();
}

[[gnu::noinline]]
static void overflow_stack(unsigned int depth) {
    volatile char buffer[4096];
    for (size_t i = 0; i < sizeof(buffer); ++i) {
        buffer[i] = static_cast<char>(depth);
    }
    overflow_stack(depth + 1);
    // Keep each frame alive in optimized builds.
    __asm__ volatile("" : : "r"(&buffer) : "memory");
}

static void *overflow_thread(void *) {
    log_crash();
    overflow_stack(0);
    return nullptr;
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashStackOverflow(JNIEnv *env, jclass cls) {
    pthread_t thread;
    int error = pthread_create(&thread, nullptr, overflow_thread, nullptr);
    if (error != 0) {
        scenario_failed("create overflow thread", error);
        return;
    }
    pthread_join(thread, nullptr);
}

static int crash_ready = 0;
static int crash_go = 0;

static void *concurrent_thread(void *) {
    __atomic_store_n(&crash_ready, 1, __ATOMIC_RELEASE);
    while (!__atomic_load_n(&crash_go, __ATOMIC_ACQUIRE)) {}
    trigger_null_deref();
    return nullptr;
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashConcurrent(JNIEnv *env, jclass cls) {
    crash_ready = 0;
    crash_go = 0;
    pthread_t thread;
    int error = pthread_create(&thread, nullptr, concurrent_thread, nullptr);
    if (error != 0) {
        scenario_failed("create concurrent thread", error);
        return;
    }
    while (!__atomic_load_n(&crash_ready, __ATOMIC_ACQUIRE)) {}
    log_crash();
    __atomic_store_n(&crash_go, 1, __ATOMIC_RELEASE);
    trigger_null_deref();
}

static pthread_mutex_t idle_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t idle_condition = PTHREAD_COND_INITIALIZER;
static unsigned int idle_count = 0;
static bool idle_done = false;

static void *idle_thread(void *) {
    pthread_mutex_lock(&idle_mutex);
    ++idle_count;
    if (idle_count == 500) {
        pthread_cond_broadcast(&idle_condition);
    }
    while (!idle_done) {
        pthread_cond_wait(&idle_condition, &idle_mutex);
    }
    pthread_mutex_unlock(&idle_mutex);
    return nullptr;
}

JNIEXPORT void JNICALL Java_io_sentry_samples_android_NativeSample_crashManyThreads(JNIEnv *env, jclass cls) {
    pthread_t threads[500];
    pthread_attr_t attributes;
    int error = pthread_attr_init(&attributes);
    if (error != 0) {
        scenario_failed("initialize thread attributes", error);
        return;
    }
    error = pthread_attr_setstacksize(&attributes, 64 * 1024);
    if (error != 0) {
        pthread_attr_destroy(&attributes);
        scenario_failed("set idle thread stack size", error);
        return;
    }
    idle_count = 0;
    idle_done = false;
    for (size_t i = 0; i < 500; ++i) {
        error = pthread_create(&threads[i], &attributes, idle_thread, nullptr);
        if (error != 0) {
            pthread_attr_destroy(&attributes);
            pthread_mutex_lock(&idle_mutex);
            idle_done = true;
            pthread_cond_broadcast(&idle_condition);
            pthread_mutex_unlock(&idle_mutex);
            for (size_t j = 0; j < i; ++j) {
                pthread_join(threads[j], nullptr);
            }
            scenario_failed("create 500 idle threads", error);
            return;
        }
    }
    pthread_attr_destroy(&attributes);
    pthread_mutex_lock(&idle_mutex);
    while (idle_count != 500) {
        pthread_cond_wait(&idle_condition, &idle_mutex);
    }
    pthread_mutex_unlock(&idle_mutex);
    log_crash();
    trigger_null_deref();
}

}
