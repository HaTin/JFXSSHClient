#!/bin/bash
# JFX SSH Client — macOS 启动器（在 Finder 里可双击运行）
#
# 需要：自带 JavaFX 的 JDK 21（推荐 Azul Zulu FX 21，按你的芯片选 aarch64 / x64）。
# 若设置了 JAVA_HOME 指向 Zulu FX，会优先使用；否则用 PATH 上的 java。
#
# 把本文件与 jfxssh.jar 放在同一目录。

cd "$(dirname "$0")" || exit 1

JAVA="java"
if [ -n "$JAVA_HOME" ]; then
  JAVA="$JAVA_HOME/bin/java"
fi

exec "$JAVA" --add-modules javafx.controls,javafx.swing -jar jfxssh.jar
