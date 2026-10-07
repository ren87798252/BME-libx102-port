#!/data/data/com.termux/files/usr/bin/bash
# 在 Termux 用户身份 + 全局 mount namespace 下执行 Termux 命令
#
#   su -M 10241 -c "bash /sdcard/bme-build/txrun.sh <命令...>"
#
# 自动注入 Termux 环境与本地 Clash 代理（127.0.0.1:7890）。
set -u

TX=/data/data/com.termux/files/usr
H=/data/data/com.termux/files/home
PROXY=http://127.0.0.1:7890

export PREFIX=$TX
export HOME=$H
export PATH=$TX/bin:$TX/bin/applets:/system/bin
export LD_LIBRARY_PATH=$TX/lib
export TMPDIR=$TX/tmp
export ANDROID_HOME=$H/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export http_proxy=$PROXY https_proxy=$PROXY
export HTTP_PROXY=$PROXY HTTPS_PROXY=$PROXY
export JAVA_HOME=$TX/lib/jvm/java-17-openjdk

exec "$@"
