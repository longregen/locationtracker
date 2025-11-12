{
  description = "LocationTracker Android APK Build";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, android-nixpkgs }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        # Android SDK configuration
        androidSdk = android-nixpkgs.sdk.${system} (sdkPkgs: with sdkPkgs; [
          cmdline-tools-latest
          build-tools-35-0-0
          platform-tools
          platforms-android-36
          platforms-android-33
          ndk-27-2-12479018
          emulator
        ]);

        # Build script for armeabi-v7a debug APK
        buildScript = pkgs.writeShellScriptBin "build-apk" ''
          set -e

          export ANDROID_HOME="${androidSdk}/share/android-sdk"
          export ANDROID_SDK_ROOT="$ANDROID_HOME"
          export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.2.12479018"
          export JAVA_HOME="${pkgs.jdk17.home}"
          export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$JAVA_HOME/bin:$PATH"

          echo "Building ARM v7 debug APK..."
          echo "ANDROID_HOME: $ANDROID_HOME"
          echo "JAVA_HOME: $JAVA_HOME"

          # Grant execute permission
          chmod +x ./gradlew

          # Build only armeabi-v7a debug APK using ABI filter
          ./gradlew assembleDebug \
            -Pandroid.injected.abi=armeabi-v7a \
            --parallel \
            --build-cache \
            --configuration-cache \
            --no-daemon \
            --stacktrace

          echo "Build complete!"

          # Find the APK in possible locations
          APK_PATH=""
          if [ -f "app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk" ]; then
            APK_PATH="app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk"
          elif [ -f "app/build/outputs/apk/armeabi-v7a/debug/app-armeabi-v7a-debug.apk" ]; then
            APK_PATH="app/build/outputs/apk/armeabi-v7a/debug/app-armeabi-v7a-debug.apk"
          fi

          if [ -n "$APK_PATH" ]; then
            echo "APK location: $APK_PATH"
            ls -lh "$APK_PATH"
          else
            echo "ERROR: APK not found at expected locations!"
            echo "Checking all APK outputs:"
            find app/build/outputs/apk -name "*.apk" -type f || true
            exit 1
          fi
        '';

      in
      {
        packages = {
          default = self.packages.${system}.apk;

          # Note: The APK build requires network access for Gradle dependencies
          # Use 'nix develop' and run the build script for actual builds
          apk = buildScript;
        };

        # Development shell with Android SDK and tools
        devShells.default = pkgs.mkShell {
          buildInputs = [
            androidSdk
            pkgs.jdk17
            pkgs.gradle
            buildScript
          ];

          shellHook = ''
            export ANDROID_HOME="${androidSdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.2.12479018"
            export JAVA_HOME="${pkgs.jdk17.home}"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$JAVA_HOME/bin:$PATH"

            echo "Android development environment loaded"
            echo "ANDROID_HOME: $ANDROID_HOME"
            echo "JAVA_HOME: $JAVA_HOME"
            echo ""
            echo "To build the ARM v7 debug APK, run: build-apk"
          '';
        };

        # Apps that can be run with 'nix run'
        apps = {
          default = self.apps.${system}.build;

          build = {
            type = "app";
            program = "${buildScript}/bin/build-apk";
          };
        };
      }
    );
}
