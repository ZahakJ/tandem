# Development shell for building Tandem on Nix/NixOS: nix-shell --run './gradlew assembleDebug'
let
  pkgs = import <nixpkgs> { config = { allowUnfree = true; android_sdk.accept_license = true; }; };
  sdk = (pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "34" ];
    buildToolsVersions = [ "34.0.0" ];
    includeEmulator = false;
    includeSystemImages = false;
    includeNDK = false;
  }).androidsdk;
in pkgs.mkShell {
  packages = [ sdk pkgs.jdk17 pkgs.android-tools ];
  ANDROID_HOME = "${sdk}/libexec/android-sdk";
  # The aapt2 that Gradle downloads is a generic Linux binary; use the one from the Nix SDK instead.
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/libexec/android-sdk/build-tools/34.0.0/aapt2";
}
