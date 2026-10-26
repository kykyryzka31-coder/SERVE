# Source this file from the repository root: source scripts/cloud-env.sh
export ANDROID_TOOLS_DIR="${ANDROID_TOOLS_DIR:-/workspace/.android-tools}"
export ANDROID_HOME="$ANDROID_TOOLS_DIR/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$ANDROID_TOOLS_DIR/user-home"
export GRADLE_USER_HOME="$ANDROID_TOOLS_DIR/gradle-home"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/19.0/bin:$PATH"
export JAVA_HOME="$ANDROID_TOOLS_DIR/jdk-21.0.13+9"
export PATH="$JAVA_HOME/bin:$PATH"
