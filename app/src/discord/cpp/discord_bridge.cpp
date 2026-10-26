// Tandem's bridge to Discord's Social SDK (a C++ library on Android): the few calls the Discord status needs, for
// DiscordNative in DiscordBackend.kt. Every function here and every callback runs on the backend's one thread: the
// SDK runs its callbacks inside RunCallbacks, which only tick() calls.

#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#include <jni.h>

#include <memory>
#include <optional>
#include <string>

namespace {

JavaVM* vm = nullptr;
jclass bridge = nullptr;
jmethodID onTokens, onAuthorizeFailed, onStatus, onTokenExpiring, onDone;
std::shared_ptr<discordpp::Client> client;
uint64_t appId = 0;

JNIEnv* env() {
    JNIEnv* e = nullptr;
    vm->GetEnv(reinterpret_cast<void**>(&e), JNI_VERSION_1_6);
    return e;
}

// Texts cross as UTF-8 bytes: JNI's own string functions use a modified UTF-8 that breaks emoji.
jbyteArray bytes(JNIEnv* e, const std::string& s) {
    jbyteArray a = e->NewByteArray(static_cast<jsize>(s.size()));
    e->SetByteArrayRegion(a, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte*>(s.data()));
    return a;
}

std::optional<std::string> text(JNIEnv* e, jbyteArray a) {
    if (a == nullptr) return std::nullopt;
    std::string s(static_cast<size_t>(e->GetArrayLength(a)), '\0');
    e->GetByteArrayRegion(a, 0, static_cast<jsize>(s.size()), reinterpret_cast<jbyte*>(s.data()));
    return s;
}

std::string chars(JNIEnv* e, jstring s) {
    const char* c = e->GetStringUTFChars(s, nullptr);
    std::string out(c);
    e->ReleaseStringUTFChars(s, c);
    return out;
}

// Calls back into Java; an exception thrown there is cleared, never left pending in the SDK.
template <typename... A> void call(jmethodID m, A... args) {
    JNIEnv* e = env();
    e->CallStaticVoidMethod(bridge, m, args...);
    if (e->ExceptionCheck()) e->ExceptionClear();
}

void done(const discordpp::ClientResult& r) {
    JNIEnv* e = env();
    jbyteArray err = bytes(e, r.Error());
    call(onDone, static_cast<jboolean>(r.Successful()), err);
    e->DeleteLocalRef(err);
}

// GetToken and RefreshToken. "invalid_grant" means the refresh token is no longer any good (revoked, expired).
void tokens(discordpp::ClientResult r, std::string access, std::string refresh, discordpp::AuthorizationTokenType,
            int32_t expiresIn, std::string) {
    JNIEnv* e = env();
    bool rejected = !r.Successful() && r.ResponseBody().find("invalid_grant") != std::string::npos;
    jbyteArray a = bytes(e, access), f = bytes(e, refresh), err = bytes(e, r.Error());
    call(onTokens, static_cast<jboolean>(r.Successful()), a, f, static_cast<jint>(expiresIn), err, static_cast<jboolean>(rejected));
    e->DeleteLocalRef(a);
    e->DeleteLocalRef(f);
    e->DeleteLocalRef(err);
}

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* v, void*) {
    vm = v;
    return JNI_VERSION_1_6;
}

#define BRIDGE(name) extern "C" JNIEXPORT void JNICALL Java_io_github_zahakj_tandem_DiscordNative_##name

BRIDGE(create)(JNIEnv* e, jclass cls, jlong id) {
    if (client) return;
    bridge = static_cast<jclass>(e->NewGlobalRef(cls));
    onTokens = e->GetStaticMethodID(cls, "tokens", "(Z[B[BI[BZ)V");
    onAuthorizeFailed = e->GetStaticMethodID(cls, "authorizeFailed", "([B)V");
    onStatus = e->GetStaticMethodID(cls, "status", "(III)V");
    onTokenExpiring = e->GetStaticMethodID(cls, "tokenExpiring", "()V");
    onDone = e->GetStaticMethodID(cls, "done", "(Z[B)V");
    appId = static_cast<uint64_t>(id);
    client = std::make_shared<discordpp::Client>();
    client->SetApplicationId(appId);
    client->SetStatusChangedCallback([](discordpp::Client::Status s, discordpp::Client::Error err, int32_t detail) {
        call(onStatus, static_cast<jint>(s), static_cast<jint>(err), static_cast<jint>(detail));
    });
    client->SetTokenExpirationCallback([] { call(onTokenExpiring); });
}

BRIDGE(tick)(JNIEnv*, jclass) { discordpp::RunCallbacks(); }

// Account linking for a public client: the SDK makes the PKCE verifier, opens Discord or a browser, and the code that
// comes back is exchanged for tokens right here, without any server.
BRIDGE(authorize)(JNIEnv*, jclass) {
    auto verifier = client->CreateAuthorizationCodeVerifier();
    discordpp::AuthorizationArgs args{};
    args.SetClientId(appId);
    args.SetScopes(discordpp::Client::GetDefaultPresenceScopes());
    args.SetCodeChallenge(verifier.Challenge());
    client->Authorize(args, [verifier](discordpp::ClientResult r, std::string code, std::string redirectUri) {
        if (!r.Successful()) {
            JNIEnv* e = env();
            jbyteArray err = bytes(e, r.Error());
            call(onAuthorizeFailed, err);
            e->DeleteLocalRef(err);
            return;
        }
        client->GetToken(appId, code, verifier.Verifier(), redirectUri, tokens);
    });
}

BRIDGE(refresh)(JNIEnv* e, jclass, jstring token) { client->RefreshToken(appId, chars(e, token), tokens); }

BRIDGE(connect)(JNIEnv* e, jclass, jstring token, jboolean connect) {
    bool andConnect = connect;
    client->UpdateToken(discordpp::AuthorizationTokenType::Bearer, chars(e, token), [andConnect](discordpp::ClientResult r) {
        if (!r.Successful()) {
            // Reported as a failed connection, so the backend stops waiting.
            call(onStatus, static_cast<jint>(0), static_cast<jint>(-1), static_cast<jint>(0));
        } else if (andConnect) {
            client->Connect();
        }
    });
}

BRIDGE(update)(JNIEnv* e, jclass, jbyteArray name, jbyteArray details, jlong startSec, jbyteArray image, jbyteArray imageText) {
    discordpp::Activity a;
    a.SetType(discordpp::ActivityTypes::Playing);
    if (auto n = text(e, name)) a.SetName(*n);
    a.SetDetails(text(e, details));
    discordpp::ActivityTimestamps t;
    t.SetStart(static_cast<uint64_t>(startSec));
    a.SetTimestamps(t);
    discordpp::ActivityAssets assets;
    assets.SetLargeImage(text(e, image));
    assets.SetLargeText(text(e, imageText));
    a.SetAssets(assets);
    client->UpdateRichPresence(a, [](discordpp::ClientResult r) { done(r); });
}

BRIDGE(clear)(JNIEnv*, jclass) { client->ClearRichPresence(); }

BRIDGE(disconnect)(JNIEnv*, jclass) { client->Disconnect(); }

BRIDGE(revoke)(JNIEnv* e, jclass, jstring token) {
    client->RevokeToken(appId, chars(e, token), [](discordpp::ClientResult r) { done(r); });
}
