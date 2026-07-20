// TamperGuard – native (JNI) runtime anti-tamper primitives for CoreGuard.
//
// Detection logic lives in native code because Java/Kotlin reflection calls are
// trivial to trace and hook with tools like Frida. Native code is harder to
// analyse dynamically and lets us call syscalls (ptrace) and read the process's
// own /proc entries directly.
//
// Every function is defensive: on any error it returns a benign value so the
// Kotlin layer degrades gracefully rather than crashing the app.

#include <jni.h>
#include <android/log.h>

#include <sys/ptrace.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>

#include <cstdio>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>

#define LOG_TAG "TamperGuard"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

// Ports the Frida server / gadget listen on by default.
constexpr int kFridaPorts[] = {27042, 27043};

// Substrings that indicate an instrumentation / hooking framework has mapped a
// shared object into our address space.
const char *const kHookLibMarkers[] = {
        "frida", "gum-js", "gadget", "libgadget",
        "xposed", "lspatch", "substrate", "libsubstrate", "epic.so"};

// Thread/process names created by Frida when it injects into a process.
const char *const kFridaProcMarkers[] = {
        "frida", "gum-js-loop", "gmain", "gdbus", "pool-frida", "linjector"};

// Mount-point markers that reveal Magisk / KernelSU style systemless root.
const char *const kRootMountMarkers[] = {
        "magisk", "/sbin/.magisk", "KSU", "kernelsu", "/.magisk/"};

// Baseline checksum of our own executable code segment, captured at load time.
uint64_t g_text_baseline = 0;
bool g_baseline_ready = false;
bool g_ptrace_protected = false;

// FNV-1a 64-bit hash – small, dependency-free, good enough for integrity diffs.
uint64_t fnv1a(const uint8_t *data, size_t len, uint64_t seed = 1469598103934665603ULL) {
    uint64_t hash = seed;
    for (size_t i = 0; i < len; ++i) {
        hash ^= data[i];
        hash *= 1099511628211ULL;
    }
    return hash;
}

std::string read_small_file(const char *path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return {};
    std::string out;
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        out.append(buf, static_cast<size_t>(n));
    }
    close(fd);
    return out;
}

// Hashes the r-xp mapping(s) belonging to libtamperguard.so. This is the code
// we most care about protecting; inline hooks (e.g. Frida) rewrite these bytes.
uint64_t compute_text_checksum() {
    FILE *maps = fopen("/proc/self/maps", "re");
    if (!maps) return 0;

    uint64_t hash = 1469598103934665603ULL;
    bool hashed_any = false;
    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        // Only executable, non-writable regions of our own library.
        if (!strstr(line, "r-xp")) continue;
        if (!strstr(line, "libtamperguard.so")) continue;

        uintptr_t start = 0, end = 0;
        if (sscanf(line, "%lx-%lx", (unsigned long *) &start, (unsigned long *) &end) != 2) {
            continue;
        }
        if (end <= start) continue;

        const auto *region = reinterpret_cast<const uint8_t *>(start);
        size_t len = end - start;
        hash = fnv1a(region, len, hash);
        hashed_any = true;
    }
    fclose(maps);
    return hashed_any ? hash : 0;
}

bool contains_any(const std::string &haystack, const char *const *needles, size_t count) {
    for (size_t i = 0; i < count; ++i) {
        if (haystack.find(needles[i]) != std::string::npos) return true;
    }
    return false;
}

jstring to_jstring(JNIEnv *env, const std::string &s) {
    return env->NewStringUTF(s.c_str());
}

} // namespace

extern "C" {

// Reads TracerPid from /proc/self/status. Non-zero means a debugger/tracer is
// attached to this process.
JNIEXPORT jint JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeTracerPid(JNIEnv *, jobject) {
    std::string status = read_small_file("/proc/self/status");
    const char *key = "TracerPid:";
    size_t pos = status.find(key);
    if (pos == std::string::npos) return 0;
    long pid = strtol(status.c_str() + pos + strlen(key), nullptr, 10);
    return static_cast<jint>(pid < 0 ? 0 : pid);
}

JNIEXPORT jboolean JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativePtraceProtected(JNIEnv *, jobject) {
    return g_ptrace_protected ? JNI_TRUE : JNI_FALSE;
}

// Attempts a short-timeout TCP connect to the known Frida ports on loopback.
JNIEXPORT jboolean JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeFridaPortOpen(JNIEnv *, jobject) {
    for (int port : kFridaPorts) {
        int sock = socket(AF_INET, SOCK_STREAM, 0);
        if (sock < 0) continue;

        // Non-blocking connect with a select() timeout so we never hang.
        int flags = fcntl(sock, F_GETFL, 0);
        fcntl(sock, F_SETFL, flags | O_NONBLOCK);

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(port);
        inet_pton(AF_INET, "127.0.0.1", &addr.sin_addr);

        int rc = connect(sock, reinterpret_cast<sockaddr *>(&addr), sizeof(addr));
        bool open = false;
        if (rc == 0) {
            open = true;
        } else {
            fd_set wset;
            FD_ZERO(&wset);
            FD_SET(sock, &wset);
            timeval tv{};
            tv.tv_sec = 0;
            tv.tv_usec = 200000; // 200ms
            if (select(sock + 1, nullptr, &wset, nullptr, &tv) > 0) {
                int err = 0;
                socklen_t len = sizeof(err);
                getsockopt(sock, SOL_SOCKET, SO_ERROR, &err, &len);
                open = (err == 0);
            }
        }
        close(sock);
        if (open) return JNI_TRUE;
    }
    return JNI_FALSE;
}

// Scans our own thread names for Frida's injected worker threads. Works without
// root because a process can always read its own /proc/self/task.
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeScanThreadsForFrida(JNIEnv *env, jobject) {
    DIR *dir = opendir("/proc/self/task");
    if (!dir) return to_jstring(env, "");

    std::string found;
    dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_name[0] == '.') continue;
        std::string comm_path = std::string("/proc/self/task/") + entry->d_name + "/comm";
        std::string comm = read_small_file(comm_path.c_str());
        // Trim trailing newline.
        while (!comm.empty() && (comm.back() == '\n' || comm.back() == '\r')) comm.pop_back();
        if (comm.empty()) continue;
        for (const char *marker : kFridaProcMarkers) {
            if (comm.find(marker) != std::string::npos) {
                found = comm;
                break;
            }
        }
        if (!found.empty()) break;
    }
    closedir(dir);
    return to_jstring(env, found);
}

// Returns the first suspicious mapped library path from /proc/self/maps, or "".
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeScanMapsForHooks(JNIEnv *env, jobject) {
    FILE *maps = fopen("/proc/self/maps", "re");
    if (!maps) return to_jstring(env, "");

    std::string found;
    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        std::string l(line);
        if (contains_any(l, kHookLibMarkers,
                         sizeof(kHookLibMarkers) / sizeof(kHookLibMarkers[0]))) {
            // Extract the path portion (after the first space-delimited fields).
            size_t slash = l.find('/');
            found = (slash != std::string::npos) ? l.substr(slash) : l;
            while (!found.empty() && (found.back() == '\n' || found.back() == '\r')) {
                found.pop_back();
            }
            break;
        }
    }
    fclose(maps);
    return to_jstring(env, found);
}

// Returns the first Magisk/KernelSU style mount entry from /proc/self/mounts.
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeScanMountsForRoot(JNIEnv *env, jobject) {
    std::string mounts = read_small_file("/proc/self/mounts");
    if (mounts.empty()) return to_jstring(env, "");

    std::string found;
    size_t start = 0;
    while (start < mounts.size()) {
        size_t nl = mounts.find('\n', start);
        if (nl == std::string::npos) nl = mounts.size();
        std::string entry = mounts.substr(start, nl - start);
        if (contains_any(entry, kRootMountMarkers,
                         sizeof(kRootMountMarkers) / sizeof(kRootMountMarkers[0]))) {
            found = entry;
            break;
        }
        start = nl + 1;
    }
    return to_jstring(env, found);
}

// Recomputes the executable-segment checksum and compares to the load-time
// baseline. Returns true when the code is intact (or when no baseline could be
// captured, in which case the Kotlin layer treats it as "unverifiable").
JNIEXPORT jboolean JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeTextIntact(JNIEnv *, jobject) {
    if (!g_baseline_ready || g_text_baseline == 0) return JNI_TRUE;
    uint64_t current = compute_text_checksum();
    if (current == 0) return JNI_TRUE; // could not read – do not raise a false alarm
    return (current == g_text_baseline) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeBaselineReady(JNIEnv *, jobject) {
    return (g_baseline_ready && g_text_baseline != 0) ? JNI_TRUE : JNI_FALSE;
}

// Called automatically when the library is loaded – as early as possible in the
// process lifecycle. This is where we install anti-debug and capture the code
// baseline, before any attacker-controlled code has a chance to run.
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    // Anti-debugging: self-attach with ptrace. Android permits only one tracer
    // per process, so this blocks a later gdb/lldb from attaching. It returns
    // -1 if a tracer is already present (e.g. an attached debugger).
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == 0) {
        g_ptrace_protected = true;
    } else {
        g_ptrace_protected = false;
    }

    // Capture the pristine checksum of our executable code for later integrity
    // verification against inline hooks.
    g_text_baseline = compute_text_checksum();
    g_baseline_ready = true;

    LOGI("TamperGuard initialised (ptrace_protected=%d, baseline=%d)",
         g_ptrace_protected ? 1 : 0, g_text_baseline != 0 ? 1 : 0);

    return JNI_VERSION_1_6;
}

} // extern "C"
