{
  description = "Schirmziit — Rust core, axum server, React dashboard, Android and iOS apps, Astro site";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { nixpkgs, rust-overlay, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ (import rust-overlay) ];
          # The Android SDK is unfree and its licence is accepted by using it.
          # Declaring that here is what stops every `nix develop` from stopping
          # to ask.
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        # One toolchain with every target both agents need. This is the whole
        # reason the shell exists: a Homebrew `cargo` earlier in PATH has only
        # the host std, and reports that as "the aarch64-apple-ios target may not
        # be installed" — which sends you to `rustup target add`, where it says
        # the target is already there.
        rust = pkgs.rust-bin.stable.latest.default.override {
          targets = [
            "aarch64-apple-ios"
            "aarch64-apple-ios-sim"
            "aarch64-linux-android"
            "x86_64-linux-android"
            "wasm32-unknown-unknown"
          ];
        };

        # Pinned to what android/app/build.gradle.kts asks for. A mismatch here
        # is not a warning: AGP refuses to configure against an SDK it cannot
        # find, and cargo-ndk cannot find a linker without the NDK.
        android = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "37.0" ];
          # 37.0.0 matches compileSdk; 36.0.0 is what AGP 9 itself asks for and
          # it fails the build outright if it is absent, naming the component.
          buildToolsVersions = [ "37.0.0" "36.0.0" ];
          platformToolsVersion = "37.0.1";
          includeNDK = true;
          ndkVersions = [ "29.0.14206865" ];
          includeEmulator = false;
          includeSystemImages = false;
        };

        jdk = pkgs.jdk21;

        storeSdk = "${android.androidsdk}/libexec/android-sdk";
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            rust
            jdk
            pkgs.cargo-ndk
            pkgs.sqlx-cli
            pkgs.just
            pkgs.nodejs_24
            pkgs.pnpm
            pkgs.xcodegen
            # psql, for bin/db and for looking at what the server wrote.
            pkgs.postgresql_18
            pkgs.jq
            pkgs.nushell
            android.platform-tools
          ];

          # Set here rather than in each script: the scripts re-enter this shell,
          # so anything exported here is what they see.
          ANDROID_NDK_ROOT = "${storeSdk}/ndk-bundle";
          ANDROID_NDK_HOME = "${storeSdk}/ndk-bundle";
          JAVA_HOME = "${jdk}";

          shellHook = ''
            # bin/* look for this to decide whether they still need to re-exec
            # themselves through `nix develop`.
            export SCHIRMZIIT_DEV_SHELL=1

            # AGP needs two things the /nix/store SDK cannot give it: a
            # `platforms/android-37` for `compileSdk = 37` (androidenv names the
            # directory `android-37.0`, after the package, not the API level) and
            # a writable root for the marker files it insists on writing. So the
            # SDK it is pointed at is a farm of symlinks into the store, with the
            # one alias added.
            sdk="''${XDG_CACHE_HOME:-$HOME/.cache}/schirmziit/android-sdk"
            rm -rf "$sdk"
            mkdir -p "$sdk/platforms"
            for entry in ${storeSdk}/*; do
              name="$(basename "$entry")"
              [ "$name" = platforms ] && continue
              ln -sfn "$entry" "$sdk/$name"
            done
            for platform in ${storeSdk}/platforms/*; do
              ln -sfn "$platform" "$sdk/platforms/$(basename "$platform")"
              # android-37.0 -> also reachable as android-37
              ln -sfn "$platform" "$sdk/platforms/$(basename "$platform" | cut -d. -f1)"
            done
            export ANDROID_HOME="$sdk"
            export ANDROID_SDK_ROOT="$sdk"
          '';
        };
      });
}
