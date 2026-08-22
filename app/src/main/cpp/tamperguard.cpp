// TamperGuard – native (JNI) runtime anti-tamper primitives for CoreGuard.
//
// Detection logic lives in native code because Java/Kotlin reflection calls are
// trivial to trace and hook with tools like Frida. Native code is harder to
// analyse dynamically and can read the process's own /proc entries directly.
//
// Architecture note: real-time RASP belongs here — NOT in an LLM or heavy
// on-device agent swarm. Kotlin swarm agents may poll these JNI results in the
// background and coordinate handoff; they must not replace this hot path.
// See docs/SWARM_ARCHITECTURE.md.
//
// Contract: every probe returns a single string, either
//   "OK|<payload>"            – the probe completed; payload is real evidence
//   "UNAVAILABLE|<REASON>"    – the probe could not complete
//
// A probe never degrades to a benign payload. An unreadable /proc entry means
// "unknown", not "nothing suspicious found": reporting the latter would let any
// device that hides /proc appear verified clean. Status and evidence travel
// together in one call so a caller cannot pair a status from one acquisition
// with evidence from another.

#include <jni.h>
#include <android/log.h>

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
#include <cerrno>
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

// ---------------------------------------------------------------------------
// Contract helpers
// ---------------------------------------------------------------------------

const char *const kReasonSourceReadFailed = "SOURCE_READ_FAILED";
const char *const kReasonBaselineUnavailable = "BASELINE_UNAVAILABLE";

jstring ok_result(JNIEnv *env, const std::string &payload) {
    return env->NewStringUTF(("OK|" + payload).c_str());
}

jstring unavailable_result(JNIEnv *env, const char *reason) {
    return env->NewStringUTF((std::string("UNAVAILABLE|") + reason).c_str());
}

// FNV-1a 64-bit hash – small, dependency-free, good enough for integrity diffs.
uint64_t fnv1a(const uint8_t *data, size_t len, uint64_t seed = 1469598103934665603ULL) {
    uint64_t hash = seed;
    for (size_t i = 0; i < len; ++i) {
        hash ^= data[i];
        hash *= 1099511628211ULL;
    }
    return hash;
}

// Reads a small file. `ok` distinguishes "read failed" from "file was empty",
// which the previous single-return version could not express.
std::string read_small_file(const char *path, bool *ok) {
    if (ok) *ok = false;
    int fd = open(path, O_RDONLY);
    if (fd < 0) return {};
    std::string out;
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        out.append(buf, static_cast<size_t>(n));
    }
    bool read_error = (n < 0);
    close(fd);
    if (read_error) return {};
    if (ok) *ok = true;
    return out;
}

// Anchor whose address is guaranteed to lie inside our own executable code
// segment. Used to locate that segment in /proc/self/maps without relying on
// the library's file path (which is the containing base.apk when native libs
// are loaded uncompressed straight from the APK).
extern "C" __attribute__((visibility("hidden"))) void tamperguard_code_anchor() {}

// Hashes the executable (r-xp) mapping that contains our own code. This is what
// we most care about protecting; inline hooks (e.g. Frida) rewrite these bytes.
// Returns 0 when the mapping could not be read at all.
uint64_t compute_text_checksum() {
    FILE *maps = fopen("/proc/self/maps", "re");
    if (!maps) return 0;

    const auto anchor = reinterpret_cast<uintptr_t>(&tamperguard_code_anchor);
    uint64_t hash = 0;
    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        uintptr_t start = 0, end = 0;
        char perms[8] = {0};
        if (sscanf(line, "%lx-%lx %7s", (unsigned long *) &start,
                   (unsigned long *) &end, perms) != 3) {
            continue;
        }
        if (perms[2] != 'x') continue;          // must be executable
        if (anchor < start || anchor >= end) continue; // must contain our code
        if (end <= start) continue;

        const auto *region = reinterpret_cast<const uint8_t *>(start);
        hash = fnv1a(region, end - start);
        break;
    }
    fclose(maps);
    return hash;
}

bool contains_any(const std::string &haystack, const char *const *needles, size_t count) {
    for (size_t i = 0; i < count; ++i) {
        if (haystack.find(needles[i]) != std::string::npos) return true;
    }
    return false;
}

} // namespace

extern "C" {

// Reads TracerPid from /proc/self/status. Non-zero means a debugger/tracer is
// attached. An unreadable or unexpectedly formatted status file is UNAVAILABLE:
// it must not be reported as "no tracer".
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeTracerProbe(JNIEnv *env, jobject) {
    bool ok = false;
    std::string status = read_small_file("/proc/self/status", &ok);
    if (!ok || status.empty()) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }

    const char *key = "TracerPid:";
    size_t pos = status.find(key);
    if (pos == std::string::npos) {
        // The source was readable but did not contain the field we rely on.
        return unavailable_result(env, kReasonSourceReadFailed);
    }

    long pid = strtol(status.c_str() + pos + strlen(key), nullptr, 10);
    if (pid < 0) pid = 0;
    return ok_result(env, std::to_string(pid));
}

// Attempts a short-timeout TCP connect to the known Frida ports on loopback.
// If no socket could be created at all we cannot probe, so report UNAVAILABLE
// instead of "no Frida server".
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeFridaPortProbe(JNIEnv *env, jobject) {
    bool probed_any = false;

    for (int port : kFridaPorts) {
        int sock = socket(AF_INET, SOCK_STREAM, 0);
        if (sock < 0) continue;
        probed_any = true;

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
        if (open) return ok_result(env, "1");
    }

    if (!probed_any) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }
    return ok_result(env, "0");
}

// Scans our own thread names for Frida's injected worker threads. Works without
// root because a process can always read its own /proc/self/task. An unreadable
// task directory is UNAVAILABLE, not "no injected thread".
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeFridaThreadProbe(JNIEnv *env, jobject) {
    DIR *dir = opendir("/proc/self/task");
    if (!dir) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }

    std::string found;
    bool read_any_comm = false;
    dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_name[0] == '.') continue;
        std::string comm_path = std::string("/proc/self/task/") + entry->d_name + "/comm";
        bool ok = false;
        std::string comm = read_small_file(comm_path.c_str(), &ok);
        if (!ok) continue;
        read_any_comm = true;
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

    // Every process has at least its own main thread. Reading none means the
    // source was not really observable.
    if (!read_any_comm) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }
    return ok_result(env, found);
}

// Returns the first suspicious mapped library path from /proc/self/maps.
// An unreadable maps file is UNAVAILABLE, not "no hooks".
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeHookedLibraryProbe(JNIEnv *env, jobject) {
    FILE *maps = fopen("/proc/self/maps", "re");
    if (!maps) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }

    std::string found;
    bool read_any_line = false;
    char line[512];
    while (fgets(line, sizeof(line), maps)) {
        read_any_line = true;
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

    if (!read_any_line) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }
    return ok_result(env, found);
}

// Returns the first Magisk/KernelSU style mount entry from /proc/self/mounts.
// An unreadable or empty mounts file is UNAVAILABLE, not "no root mount".
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeRootMountProbe(JNIEnv *env, jobject) {
    bool ok = false;
    std::string mounts = read_small_file("/proc/self/mounts", &ok);
    if (!ok || mounts.empty()) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }

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
    return ok_result(env, found);
}

// Recomputes the executable-segment checksum and compares it to the load-time
// baseline, reporting baseline and comparison status from one acquisition.
//
// Neither a missing baseline nor an unreadable mapping may be reported as
// intact code: both used to return JNI_TRUE, which meant a hooked process that
// blocked /proc/self/maps was indistinguishable from a pristine one.
JNIEXPORT jstring JNICALL
Java_com_coldboar_coreguard_NativeTamperGuard_nativeCodeIntegrityProbe(JNIEnv *env, jobject) {
    if (!g_baseline_ready || g_text_baseline == 0) {
        return unavailable_result(env, kReasonBaselineUnavailable);
    }

    uint64_t current = compute_text_checksum();
    if (current == 0) {
        return unavailable_result(env, kReasonSourceReadFailed);
    }

    return ok_result(env, current == g_text_baseline ? "1" : "0");
}

// Called automatically when the library is loaded – as early as possible in the
// process lifecycle. Capture the code baseline before any attacker-controlled
// code has a chance to run. Debugger detection remains passive: a successful
// PTRACE_TRACEME call would mark our parent as a tracer and corrupt TracerPid.
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    // Capture the pristine checksum of our executable code for later integrity
    // verification against inline hooks. The baseline is only "ready" when a
    // real checksum was captured.
    g_text_baseline = compute_text_checksum();
    g_baseline_ready = (g_text_baseline != 0);

    LOGI("TamperGuard initialised (baseline=%d)", g_baseline_ready ? 1 : 0);

    return JNI_VERSION_1_6;
}

} // extern "C"
