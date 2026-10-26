# Tandem's JNI bridge to Discord's Social SDK, built only when the SDK is in third_party/discord (app/build.gradle.kts).
# The SDK's library and headers come from its .aar through Prefab.
cmake_minimum_required(VERSION 3.22.1)
project(tandem_discord CXX)

find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(tandem_discord SHARED discord_bridge.cpp)
target_compile_features(tandem_discord PRIVATE cxx_std_17)
target_link_libraries(tandem_discord PRIVATE discord_partner_sdk::discord_partner_sdk)
