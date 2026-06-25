#!/usr/bin/env bash
#
# JFX SSH Client 运行脚本
# 用法：在桌面的终端里执行  ./run.sh
# 说明：必须在「桌面会话内的终端」运行，这样 DISPLAY 已设置，窗口才会显示。
#
set -e

# 使用自带 JavaFX 的 Zulu FX JDK 21（可用环境变量覆盖）
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/zulu-fx-21-arm64}"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$(dirname "$0")"

echo ">> JAVA_HOME = $JAVA_HOME"
echo ">> 编译..."
mvn -q compile

echo ">> 启动 JFX SSH Client..."
mvn -q exec:exec
