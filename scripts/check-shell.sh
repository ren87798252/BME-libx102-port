#!/system/bin/sh
# 你所在的 shell 到底是不是 Termux？
echo "PREFIX      = ${PREFIX:-<未设置>}"
echo "TERMUX_VER  = ${TERMUX_VERSION:-<未设置>}"
echo "shell       = $(readlink -f /proc/$$/exe 2>/dev/null || echo '?')"
echo "pkg         = $(command -v pkg || echo '<无>')"
echo "java        = $(command -v java || echo '<无>')"
echo
if [ -n "${PREFIX:-}" ] && [ -x "${PREFIX:-}/bin/pkg" ]; then
  echo "==> 是 Termux，可以继续"
else
  echo "==> 不是 Termux！必须在 Termux 应用自己的终端里运行"
fi
