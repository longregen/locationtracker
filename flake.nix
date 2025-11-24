{
  description = "LocationTracker Android APK Build - Hermetic & Reproducible";

  # This flake provides a hermetic (fully offline after initial setup) build
  # for the LocationTracker Android app using Nix + Gradle dependency caching.
  #
  # Build the APK:
  #   nix build .#apk
  #
  # Update Gradle dependencies (when build.gradle changes):
  #   nix build .#apk.mitmCache.updateScript
  #   ./result    # Run the generated script to update deps.json
  #
  # Development shell:
  #   nix develop

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
          system-images-android-33-google-apis-x86-64
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

        # E2E test runner script
        e2eTestScript = pkgs.writeShellScriptBin "run-e2e-tests" ''
          set -e

          export ANDROID_HOME="${androidSdk}/share/android-sdk"
          export ANDROID_SDK_ROOT="$ANDROID_HOME"
          export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.2.12479018"
          export JAVA_HOME="${pkgs.jdk17.home}"
          export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$ANDROID_HOME/emulator:$JAVA_HOME/bin:$PATH"

          echo "=== LocationTracker E2E Test Runner (Nix) ==="
          echo "ANDROID_HOME: $ANDROID_HOME"
          echo "JAVA_HOME: $JAVA_HOME"
          echo ""

          # Build debug and test APKs
          echo "Building APKs..."
          gradle assembleDebug assembleDebugAndroidTest \
            --parallel \
            --build-cache \
            --configuration-cache \
            --no-daemon \
            --stacktrace

          echo ""
          echo "=== Creating Android Emulator AVD ==="

          # Create AVD if it doesn't exist
          AVD_NAME="test_avd_33"
          if ! avdmanager list avd | grep -q "$AVD_NAME"; then
            echo "Creating AVD: $AVD_NAME"
            echo "no" | avdmanager create avd \
              --force \
              --name "$AVD_NAME" \
              --package "system-images;android-33;google_apis;x86_64" \
              --abi google_apis/x86_64
          else
            echo "AVD $AVD_NAME already exists"
          fi

          echo ""
          echo "=== Starting Android Emulator ==="

          # Start emulator in background
          $ANDROID_HOME/emulator/emulator \
            -avd "$AVD_NAME" \
            -no-window \
            -gpu swiftshader_indirect \
            -noaudio \
            -no-boot-anim \
            -camera-back none \
            -no-snapshot-save &

          EMULATOR_PID=$!
          echo "Emulator started with PID: $EMULATOR_PID"

          # Wait for device
          echo "Waiting for device to boot..."
          adb wait-for-device
          echo "Device connected"

          # Wait for boot to complete
          echo "Waiting for boot to complete..."
          adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
          adb shell 'while [ -z "$(pm list packages)" ]; do sleep 1; done'
          adb shell 'while [ "$(getprop init.svc.bootanim)" != "stopped" ]; do sleep 1; done'

          # Wait for critical services
          echo "Waiting for services..."
          for i in {1..30}; do
            if adb shell service check input 2>/dev/null | grep -q "Service input:"; then
              echo "Input service ready"
              break
            fi
            echo "Waiting for input service... ($i/30)"
            sleep 2
          done

          for i in {1..30}; do
            if adb shell service check settings 2>/dev/null | grep -q "Service settings:"; then
              echo "Settings service ready"
              break
            fi
            echo "Waiting for settings service... ($i/30)"
            sleep 2
          done

          # Stabilization period
          echo "Stabilization period..."
          sleep 5

          echo ""
          echo "=== Configuring Emulator ==="

          # Disable animations
          adb shell settings put global window_animation_scale 0.0
          adb shell settings put global transition_animation_scale 0.0
          adb shell settings put global animator_duration_scale 0.0

          # Allow mock location
          adb shell appops set com.locationtracker android:mock_location allow || echo "Mock location permission already set"

          # Set initial location to London
          echo "Setting initial location to London (51.5074, -0.1278)"
          adb emu geo fix -0.1278 51.5074
          sleep 2

          echo ""
          echo "=== Installing APKs ==="

          adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
          adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

          echo ""
          echo "=== Granting Permissions ==="

          adb shell pm grant com.locationtracker android.permission.ACCESS_FINE_LOCATION
          adb shell pm grant com.locationtracker android.permission.ACCESS_COARSE_LOCATION
          adb shell pm grant com.locationtracker android.permission.POST_NOTIFICATIONS
          adb shell mkdir -p /sdcard/Pictures

          echo ""
          echo "=== Running E2E Tests ==="

          mkdir -p test-results
          adb shell am instrument -w -r -e debug false -e class com.locationtracker.LocationTrackerE2ETest \
            com.locationtracker.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/test-output.txt

          TEST_EXIT_CODE=''${PIPESTATUS[0]}
          echo "Test execution completed. Exit code: $TEST_EXIT_CODE"

          echo ""
          echo "=== Pulling Screenshots ==="

          mkdir -p screenshots
          adb pull /sdcard/Pictures/ screenshots/ || echo "No screenshots found"

          if [ -d "screenshots/Pictures" ]; then
            echo "Screenshots captured:"
            ls -la screenshots/Pictures/
          else
            echo "No screenshots directory"
          fi

          echo ""
          echo "=== Stopping Emulator ==="
          kill $EMULATOR_PID 2>/dev/null || true

          echo ""
          echo "=== E2E Test Run Complete ==="
          exit $TEST_EXIT_CODE
        '';

      in
      {
        packages = rec {
          default = apk;

          # Hermetic APK build using gradle.fetchDeps
          apk = pkgs.stdenv.mkDerivation {
            pname = "locationtracker";
            version = "1.0.0";

            src = ./.;

            nativeBuildInputs = [
              androidSdk
              pkgs.jdk17
              pkgs.gradle
            ];

            mitmCache = pkgs.gradle.fetchDeps {
              pkg = apk;
              data = ./deps.json;
              # Disable bubblewrap sandboxing for GitHub Actions compatibility
              useBwrap = false;
            };

            buildPhase = ''
              export ANDROID_HOME="${androidSdk}/share/android-sdk"
              export ANDROID_SDK_ROOT="$ANDROID_HOME"
              export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.2.12479018"
              export JAVA_HOME="${pkgs.jdk17.home}"
              export GRADLE_USER_HOME="$mitmCache"

              # Build only armeabi-v7a debug APK using Gradle with cached dependencies
              gradle assembleDebug \
                -Pandroid.injected.abi=armeabi-v7a \
                --offline \
                --no-daemon \
                --stacktrace
            '';

            installPhase = ''
              mkdir -p $out

              # Find and copy the APK from possible locations
              if [ -f "app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk" ]; then
                cp app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk $out/
              elif [ -f "app/build/outputs/apk/armeabi-v7a/debug/app-armeabi-v7a-debug.apk" ]; then
                cp app/build/outputs/apk/armeabi-v7a/debug/app-armeabi-v7a-debug.apk $out/
              else
                echo "ERROR: Could not find armeabi-v7a debug APK"
                find app/build/outputs/apk -name "*.apk" -type f || true
                exit 1
              fi
            '';
          };

          # Legacy build script for local development
          build-script = buildScript;
        };

        # Development shell with Android SDK and tools
        devShells.default = pkgs.mkShell {
          buildInputs = [
            androidSdk
            pkgs.jdk17
            pkgs.gradle
            buildScript
            e2eTestScript
          ];

          shellHook = ''
            export ANDROID_HOME="${androidSdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/27.2.12479018"
            export JAVA_HOME="${pkgs.jdk17.home}"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$ANDROID_HOME/emulator:$JAVA_HOME/bin:$PATH"

            echo "╔══════════════════════════════════════════════════════════════╗"
            echo "║  LocationTracker - Hermetic Android Build Environment       ║"
            echo "╚══════════════════════════════════════════════════════════════╝"
            echo ""
            echo "Environment:"
            echo "  ANDROID_HOME: $ANDROID_HOME"
            echo "  JAVA_HOME: $JAVA_HOME"
            echo ""
            echo "Commands:"
            echo "  build-apk              - Build ARM v7 debug APK (requires network)"
            echo "  run-e2e-tests          - Run E2E tests with emulator (Nix)"
            echo "  nix build .#apk        - Hermetic build (offline, uses deps.json)"
            echo "  nix run .#e2e-tests    - Run E2E tests (alternative)"
            echo ""
            echo "Update dependencies:"
            echo "  nix build .#apk.mitmCache.updateScript && ./result"
            echo ""
          '';
        };

        # Apps that can be run with 'nix run'
        apps = {
          default = self.apps.${system}.build;

          build = {
            type = "app";
            program = "${buildScript}/bin/build-apk";
          };

          e2e-tests = {
            type = "app";
            program = "${e2eTestScript}/bin/run-e2e-tests";
          };
        };
      }
    );
}
