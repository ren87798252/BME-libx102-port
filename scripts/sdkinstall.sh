cd "$HOME"
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
SDKM=$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager
export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Djava.net.preferIPv4Stack=true"

echo "==================== 接受许可 ===================="
yes 2>/dev/null | sh $SDKM --sdk_root="$ANDROID_HOME" --licenses 2>&1 | tail -5
echo "licenses done"

echo "==================== 安装组件 ===================="
sh $SDKM --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-37.0" \
  "build-tools;37.0.0" 2>&1 | tail -30
echo "install exit=$?"

echo "==================== 验证 ===================="
ls "$ANDROID_HOME/platforms" 2>&1
ls "$ANDROID_HOME/build-tools" 2>&1
ls "$ANDROID_HOME/platform-tools" 2>&1 | head -5
