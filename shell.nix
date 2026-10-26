# Development shell for building Tandem on Nix/NixOS: nix-shell --run './gradlew assembleDebug'
# The NDK builds the controller passthrough helper (app/src/padlink). With the Discord Social SDK in third_party/discord
# (docs/guide/building.md) it also brings the CMake that needs (or force it either way with --arg discord true / false).
{ discord ? builtins.pathExists ./third_party/discord }:
let
  pkgs = import <nixpkgs> { config = { allowUnfree = true; android_sdk.accept_license = true; }; };
  sdk = (pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "34" ];
    buildToolsVersions = [ "34.0.0" ];
    includeEmulator = false;
    includeSystemImages = false;
    includeNDK = true;
    includeCmake = discord;
    cmakeVersions = [ "3.22.1" ];
  }).androidsdk;
in pkgs.mkShell {
  packages = [ sdk pkgs.jdk17 pkgs.android-tools ];
  ANDROID_HOME = "${sdk}/libexec/android-sdk";
  # The aapt2 that Gradle downloads is a generic Linux binary; use the one from the Nix SDK instead.
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/libexec/android-sdk/build-tools/34.0.0/aapt2";
}
