#!/data/data/com.termux/files/usr/bin/bash
# 在 Termux 里跑 BME-libx102 的 assembleDebug
# 前提：root 已把源码复制到 $HOME/bme（/sdcard 对 Termux 不可读）
set -u
LOG=$HOME/build.log
mkdir -p "$LOG.d"
exec >> "$LOG" 2>&1

echo "########## 开始 $(date) ##########"
echo "uid=$(id -u)  PREFIX=$PREFIX"
echo "ANDROID_HOME=$ANDROID_HOME"
java -version 2>&1 | head -2

# 代理：Clash 未覆盖 termux uid，Java 进程需显式走本地代理
export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890"

cd "$HOME/bme" || { echo "找不到 $HOME/bme"; exit 1; }
echo "工作目录 $PWD"

echo "sdk.dir=$ANDROID_HOME" > local.properties
cat local.properties

AAPT2=$PREFIX/bin/aapt2
echo "AAPT2=$AAPT2"
"$AAPT2" version 2>&1 | head -2

chmod +x gradlew
# gradlew 的 shebang 是 #!/bin/sh，Termux 里不存在该路径，用 bash 显式执行
echo "gradlew shebang: $(head -1 gradlew)"

echo "########## gradle 开始 $(date) ##########"
bash ./gradlew :app:assembleDebug \
    --no-daemon --stacktrace \
    --console=plain \
    -Pandroid.aapt2FromMavenOverride="$AAPT2" \
    -Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=768m" \
    -Dkotlin.daemon.jvmargs="-Xmx1536m"
RC=$?
echo "########## gradle 结束 rc=$RC $(date) ##########"

echo "---- 产物 ----"
find "$HOME/bme" -name "*.apk" -print 2>/dev/null
echo "########## 全部结束 rc=$RC ##########"
