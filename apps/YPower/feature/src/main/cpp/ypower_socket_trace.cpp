#include <jni.h>
#include <android/log.h>
#include <bytehook.h>

#include <atomic>
#include <cerrno>
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <mutex>
#include <string>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#define LOG_TAG "YPowerTrace"

static std::atomic<bool> g_enabled(false);
static std::mutex g_lock;
static std::string g_package;
static std::string g_session;
static bytehook_stub_t g_stub = nullptr;
static bool g_installed = false;
static thread_local bool g_emitting = false;

static int64_t now_ms() {
    timespec ts{};
    clock_gettime(CLOCK_REALTIME, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000LL + ts.tv_nsec / 1000000LL;
}

static int64_t now_ns() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000000000LL + ts.tv_nsec;
}

static std::string escape_json(const std::string &value) {
    std::string out;
    for (char c : value) {
        if (c == '\\') out += "\\\\";
        else if (c == '"') out += "\\\"";
        else if (c == '\n') out += "\\n";
        else if (c == '\r') out += "\\r";
        else if (c == '\t') out += "\\t";
        else if (static_cast<unsigned char>(c) >= 0x20) out += c;
    }
    return out;
}

static std::string caller_source() {
    void *addr = BYTEHOOK_RETURN_ADDRESS();
    if (addr == nullptr) return "";
    Dl_info info{};
    if (dladdr(addr, &info) == 0 || info.dli_fname == nullptr) return "";
    const char *base = strrchr(info.dli_fname, '/');
    base = base == nullptr ? info.dli_fname : base + 1;
    uintptr_t offset = info.dli_fbase == nullptr ? 0
            : reinterpret_cast<uintptr_t>(addr) - reinterpret_cast<uintptr_t>(info.dli_fbase);
    char buf[256];
    snprintf(buf, sizeof(buf), "%s+0x%lx", base, static_cast<unsigned long>(offset));
    return buf;
}

static bool caller_filter(const char *caller, void *) {
    if (caller == nullptr) return false;
    std::string s(caller);
    return s.find("/system/") == std::string::npos
            && s.find("/apex/") == std::string::npos
            && s.find("/vendor/") == std::string::npos
            && s.find("/product/") == std::string::npos
            && s.find("libbytehook.so") == std::string::npos
            && s.find("libypower_socket_trace.so") == std::string::npos
            && s.find("libc.so") == std::string::npos;
}

static std::string socket_name(const sockaddr *addr, socklen_t len) {
    if (addr == nullptr || addr->sa_family != AF_UNIX) return "";
    if (len <= offsetof(sockaddr_un, sun_path)) return "";
    const auto *un = reinterpret_cast<const sockaddr_un *>(addr);
    size_t n = static_cast<size_t>(len) - offsetof(sockaddr_un, sun_path);
    if (n > sizeof(un->sun_path)) n = sizeof(un->sun_path);
    if (n == 0) return "";
    if (un->sun_path[0] == '\0') {
        if (n <= 1) return "@";
        return "@" + std::string(un->sun_path + 1, strnlen(un->sun_path + 1, n - 1));
    }
    return std::string(un->sun_path, strnlen(un->sun_path, n));
}

static void emit_event(const std::string &name, int result, int saved_errno,
                       const std::string &source, int64_t duration_ns) {
    if (!g_enabled.load(std::memory_order_relaxed) || g_emitting) return;
    g_emitting = true;

    std::string pkg;
    std::string session;
    {
        std::lock_guard<std::mutex> guard(g_lock);
        pkg = g_package;
        session = g_session;
    }

    pid_t pid = getpid();
    pid_t tid = static_cast<pid_t>(syscall(SYS_gettid));
    std::string error;
    if (result != 0 && saved_errno != 0) {
        error = "errno=" + std::to_string(saved_errno) + "(" + std::string(strerror(saved_errno)) + ")";
    }

    std::string json = "{";
    json += "\"ts\":" + std::to_string(now_ms());
    json += ",\"package\":\"" + escape_json(pkg) + "\"";
    json += ",\"sessionId\":\"" + escape_json(session) + "\"";
    json += ",\"type\":\"native_file\"";
    json += ",\"ruleId\":\"NATIVE_UNIX_SOCKET_CONNECT\"";
    json += ",\"input\":\"connect(AF_UNIX) " + escape_json(name) + "\"";
    json += ",\"value\":\"" + escape_json(name) + "\"";
    json += ",\"result\":\"" + std::to_string(result) + "\"";
    json += ",\"matched\":false";
    json += ",\"hitState\":\"CHECKED\"";
    json += ",\"exception\":\"" + escape_json(error) + "\"";
    json += ",\"source\":\"" + escape_json(source) + "\"";
    json += ",\"pid\":" + std::to_string(pid);
    json += ",\"tid\":" + std::to_string(tid);
    json += ",\"thread\":\"\"";
    json += ",\"process\":\"" + escape_json(pkg) + "\"";
    json += ",\"durationNs\":" + std::to_string(duration_ns);
    json += ",\"stack\":\"" + escape_json(source) + "\"}";
    __android_log_write(ANDROID_LOG_INFO, LOG_TAG, json.c_str());
    g_emitting = false;
}

static int proxy_connect(int fd, const sockaddr *addr, socklen_t len) {
    BYTEHOOK_STACK_SCOPE();
    int64_t start = now_ns();
    std::string source = caller_source();
    std::string name = socket_name(addr, len);
    int ret = BYTEHOOK_CALL_PREV(proxy_connect, fd, addr, len);
    int saved_errno = errno;
    if (!name.empty()) emit_event(name, ret, saved_errno, source, now_ns() - start);
    errno = saved_errno;
    return ret;
}

static void install_once() {
    if (g_installed) return;
    g_stub = bytehook_hook_partial(caller_filter, nullptr, "libc.so", "connect",
                                   reinterpret_cast<void *>(proxy_connect), nullptr, nullptr);
    g_installed = g_stub != nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_yagay_ypower_hook_NativeSocketTraceBridge_nativeEnable(
        JNIEnv *env, jclass, jstring package_name, jstring session_id) {
    const char *pkg = package_name == nullptr ? "" : env->GetStringUTFChars(package_name, nullptr);
    const char *sid = session_id == nullptr ? "" : env->GetStringUTFChars(session_id, nullptr);
    {
        std::lock_guard<std::mutex> guard(g_lock);
        g_package = pkg == nullptr ? "" : pkg;
        g_session = sid == nullptr ? "" : sid;
        install_once();
    }
    if (package_name != nullptr && pkg != nullptr) env->ReleaseStringUTFChars(package_name, pkg);
    if (session_id != nullptr && sid != nullptr) env->ReleaseStringUTFChars(session_id, sid);
    g_enabled.store(g_installed, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_yagay_ypower_hook_NativeSocketTraceBridge_nativeDisable(JNIEnv *, jclass) {
    g_enabled.store(false, std::memory_order_relaxed);
}
