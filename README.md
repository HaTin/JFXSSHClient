# JFX SSH Client

A modern, cross-platform SSH client built with JavaFX — connection management, a real terminal, SFTP, and port forwarding in one desktop app. 

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)

> 中文说明见 [下半部分](#中文说明)。

---

## Features

- **Connection management** — create / edit / delete / duplicate connections, organized into nestable groups, persisted in SQLite.
- **SSH authentication** — password and public-key. Private keys are **pasted as content and stored encrypted** in the database (with optional passphrase), not referenced by file path.
- **Terminal** — multiple tabs, UTF-8, full ANSI / 256-color, scrollback, powered by the JediTerm core. A dedicated input box keeps IME/Chinese input working on macOS.
- **SFTP** — dual-pane (local ⇄ remote) file manager with concurrent transfers, progress / speed / cancel, recursive **folder upload**, and same-name **overwrite confirmation** (with Overwrite-All / Skip-All).
- **Port forwarding** — local (`-L`), remote (`-R`), and dynamic SOCKS (`-D`). Rules are persisted per connection, run in the background after the window closes, and can auto-start on connect. One-click **Start All / Stop All**.
- **Security** — credentials encrypted with a user master password: PBKDF2-HMAC-SHA256 (600k iterations) → AES-256-GCM. The master password is never stored; the derived key lives only in memory.
- **Theming & i18n** — Light / Dark themes and English / 简体中文, both switchable live without restart.
- **Host-key verification** — known-hosts TOFU; warns on key mismatch.

## Tech stack

| | |
|---|---|
| Language / UI | Java 21, JavaFX 21 |
| SSH / SFTP / forwarding | Apache MINA SSHD (client) |
| Terminal core | JediTerm (JetBrains) via SwingNode |
| Storage | SQLite (JDBC) |
| JSON / i18n | Jackson |
| Logging | SLF4J + Logback |
| Build / test | Maven, JUnit 5 |

## Build

Requires JDK 21. The shaded runnable jar is produced at `target/jfxssh.jar`.

```bash
mvn clean package          # build + run tests
mvn -DskipTests package    # build only
```

## Run

JavaFX must be on the module path. The easiest way is a JDK that bundles JavaFX (e.g. **Azul Zulu FX 21**). The app is tuned to keep native (off-heap) memory bounded, so launch it **with the recommended JVM flags**:

```bash
java -Xmx512m -XX:MaxDirectMemorySize=128m \
  -Djdk.nio.maxCachedBufferSize=262144 \
  -XX:+UseStringDeduplication -XX:G1PeriodicGCInterval=10000 \
  --add-modules javafx.controls,javafx.swing \
  -jar target/jfxssh.jar
```

On **macOS** you can double-click `run-mac.command` (place it next to `jfxssh.jar`); it applies the flags above. See [docs/RUN_MACOS.md](docs/RUN_MACOS.md) for details.

## Project structure

```
com.xxx.jfxssh
├── launcher    app entry point
├── ui          JavaFX views (tree / terminal / dialogs / sftp / forward / ...)
├── service     business logic (interfaces + Impl)
├── ssh         SSH / SFTP / port-forward transport (Apache MINA SSHD)
├── terminal    JediTerm ⇄ SSH stream adapter
├── storage     SQLite database, repositories, entities
└── common      config, i18n, security primitives
```

Layering is strict: `UI → Service → Repository → Database`. The `ssh` and `terminal` modules are infrastructure called by services; the UI never touches them directly.

## Documentation

| Doc | Contents |
|---|---|
| [PRODUCT_REQUIREMENT.md](docs/PRODUCT_REQUIREMENT.md) | Product scope and delivery status |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layering, modules, terminal & security design |
| [DATABASE.md](docs/DATABASE.md) | SQLite schema |
| [API.md](docs/API.md) | Service-layer interfaces |
| [I18N.md](docs/I18N.md) | Internationalization scheme |
| [UI_DESIGN.md](docs/UI_DESIGN.md) | UI layout and behavior |
| [CODING_STANDARDS.md](docs/CODING_STANDARDS.md) | Coding conventions |
| [RUN_MACOS.md](docs/RUN_MACOS.md) | macOS run & memory notes |
| [TASKS.md](docs/TASKS.md) | Final feature / delivery status |

## License

[MIT](LICENSE) © 2026 HaTin

---

# 中文说明

基于 JavaFX 的现代化跨平台 SSH 客户端 —— 在一个桌面应用里集成连接管理、真实终端、SFTP 与端口转发。

## 功能特性

- **连接管理** —— 新建 / 编辑 / 删除 / 复制连接，支持可嵌套分组，数据存于 SQLite。
- **SSH 认证** —— 密码与公钥两种方式。私钥采用**粘贴内容并加密入库**（可带口令），不再以文件路径引用。
- **终端** —— 多标签、UTF-8、完整 ANSI / 256 色、回滚缓冲，基于 JediTerm 内核；底部独立输入框解决 macOS 下中文输入法问题。
- **SFTP** —— 本地 ⇄ 远程双栏文件管理器，并发传输、进度 / 速度 / 取消、递归**文件夹上传**、同名**覆盖确认**（含「全部覆盖 / 全部跳过」）。
- **端口转发** —— 本地（`-L`）、远程（`-R`）、动态 SOCKS（`-D`）。规则按连接持久化，关窗后后台继续运行，可在连接成功时自动启动；支持一键**全部启动 / 全部停止**。
- **安全** —— 凭据用主密码加密：PBKDF2-HMAC-SHA256（60 万次迭代）→ AES-256-GCM。主密码不入库，派生密钥仅驻内存。
- **主题与多语言** —— 浅色 / 深色主题、English / 简体中文，均可实时切换、无需重启。
- **主机密钥校验** —— known_hosts TOFU，指纹变更时告警。

## 技术栈

Java 21 · JavaFX 21 · Apache MINA SSHD · JediTerm · SQLite · Jackson · SLF4J/Logback · Maven · JUnit 5

## 构建

需要 JDK 21，产物为 `target/jfxssh.jar`：

```bash
mvn clean package          # 构建 + 跑测试
mvn -DskipTests package    # 仅构建
```

## 运行

JavaFX 需在模块路径上，推荐使用自带 JavaFX 的 JDK（如 **Azul Zulu FX 21**）。应用针对堆外内存做了调优，请**带推荐 JVM 参数启动**：

```bash
java -Xmx512m -XX:MaxDirectMemorySize=128m \
  -Djdk.nio.maxCachedBufferSize=262144 \
  -XX:+UseStringDeduplication -XX:G1PeriodicGCInterval=10000 \
  --add-modules javafx.controls,javafx.swing \
  -jar target/jfxssh.jar
```

**macOS** 下可双击 `run-mac.command`（与 `jfxssh.jar` 放同一目录），已内置上述参数。详见 [docs/RUN_MACOS.md](docs/RUN_MACOS.md)。

## 许可证

[MIT](LICENSE) © 2026 HaTin
