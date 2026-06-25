# 在 macOS 上运行 JFX SSH Client

由于本项目在 Linux 上构建，**无法跨平台生成 macOS 原生 `.app/.dmg`**（jpackage 只能在目标系统上打包）。
这里提供一个**跨平台可执行 fat JAR**，在 macOS 上配合自带 JavaFX 的 JDK 即可运行。

---

## 一、准备文件

1. 在本项目构建 fat JAR（已配置好）：

   ```bash
   mvn -DskipTests package
   ```

   产物：`target/jfxssh.jar`（约 21 MB，已包含 SSH / 终端 / SQLite / JSON 等全部依赖，
   SQLite 的 macOS 原生库也在其中，跨平台通用）。

2. 把这两个文件拷到你的 Mac（同一目录）：

   - `target/jfxssh.jar`
   - `run-mac.command`

---

## 二、安装 JavaFX 版 JDK 21

JavaFX 未随普通 JDK 提供，请装**自带 JavaFX 的 JDK 21**：

- **Azul Zulu FX 21**（推荐）：<https://www.azul.com/downloads/?version=java-21-lts&os=macos&package=jdk-fx>
  - Apple 芯片（M1/M2/M3…）选 **macOS aarch64**
  - Intel 芯片选 **macOS x64**
- 或 BellSoft **Liberica Full JDK 21**（同样自带 JavaFX）。

安装后确认：

```bash
/usr/libexec/java_home -V        # 看看 Zulu FX 21 的路径
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
java -version                    # 应显示 21，且为 Zulu/Liberica
```

---

## 三、运行

方式 A（命令行）：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
java --add-modules javafx.controls,javafx.swing -jar jfxssh.jar
```

方式 B（双击）：在 Finder 双击 `run-mac.command`。
- 首次可能被 Gatekeeper 拦：右键 → 打开，或在「系统设置 → 隐私与安全性」里允许。
- 若 `java` 不在 PATH，先 `export JAVA_HOME=...` 后从终端运行该脚本。

数据目录：`~/.jfxssh/`（数据库、配置、日志）。

---

## 四、（可选）在 Mac 上打成原生 .app / .dmg

在 **macOS 上**（装了上面的 Zulu FX JDK）可用 jpackage 生成自带运行时的原生应用：

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
jpackage \
  --type dmg \
  --name "JFX SSH Client" \
  --input target \
  --main-jar jfxssh.jar \
  --main-class com.xxx.jfxssh.launcher.Main \
  --runtime-image "$JAVA_HOME" \
  --java-options "--add-modules=javafx.controls,javafx.swing"
```

`--runtime-image "$JAVA_HOME"` 用的是自带 JavaFX 的 JDK，因此生成的 `.app` 自包含 JavaFX，
终端用户无需另装 JDK。生成的 `.dmg` 在当前目录。

> 注：原生打包必须在 macOS 上执行；Linux 无法产出 mac 的 .app/.dmg。
