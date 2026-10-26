#!/usr/bin/env bash
#
# Installs a self-contained Android build toolchain into ~/toolchain (no root required).
# Used to build Focus Lock on a clean Linux machine:
#
#   ./scripts/setup-toolchain.sh
#   export JAVA_HOME=~/toolchain/jdk
#   export ANDROID_HOME=~/toolchain/android-sdk
#   ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
#
set -euo pipefail

TOOLCHAIN="${TOOLCHAIN:-$HOME/toolchain}"
SDK_PLATFORM="platforms;android-36"
BUILD_TOOLS="build-tools;36.0.0"
GRADLE_VERSION="8.14.6"
CMDLINE_TOOLS="commandlinetools-linux-16111833_latest.zip"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$TOOLCHAIN"
cd "$TOOLCHAIN"

# 1. JDK 17 (Temurin, latest available 17 GA build)
if [ ! -x "$TOOLCHAIN/jdk/bin/java" ]; then
  echo "==> downloading JDK 17"
  mkdir -p jdk
  curl -sSL -o jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf jdk.tar.gz -C jdk --strip-components=1
  rm -f jdk.tar.gz
fi

export JAVA_HOME="$TOOLCHAIN/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# 2. Android SDK command-line tools, platform and build tools
if [ ! -x "$TOOLCHAIN/android-sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "==> downloading Android command-line tools"
  curl -sSL -o cmdline-tools.zip "https://dl.google.com/android/repository/$CMDLINE_TOOLS"
  mkdir -p android-sdk/cmdline-tools
  unzip -q cmdline-tools.zip -d android-sdk/cmdline-tools
  mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest
  rm -f cmdline-tools.zip
fi

export ANDROID_HOME="$TOOLCHAIN/android-sdk"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "platform-tools" "$SDK_PLATFORM" "$BUILD_TOOLS"

# 3. Gradle (the repository also contains a wrapper; this is only for machines without it)
if [ ! -x "$TOOLCHAIN/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  echo "==> downloading Gradle $GRADLE_VERSION"
  curl -sSL -o gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q gradle.zip
  rm -f gradle.zip
fi

# 4. Point the build at this SDK (local.properties is git-ignored)
echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"

cat <<EOF

Toolchain ready.

  export JAVA_HOME=$JAVA_HOME
  export ANDROID_HOME=$ANDROID_HOME
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

APK output: app/build/outputs/apk/debug/app-debug.apk
EOF
