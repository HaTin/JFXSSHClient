# 架构设计

## 分层

UI

↓

Service

↓

Repository

↓

Database

## 模块

com.xxx.jfxssh

├── launcher
├── ui
├── service        // 业务逻辑层（接口 + Impl）
├── ssh
├── terminal
├── storage        // 含 repository（数据访问）+ entity
├── common

## 分层与模块映射

UI 层          → ui

Service 层     → service

Repository 层  → storage.repository

Database       → SQLite

说明：

ssh 与 terminal 为传输 / 终端基础设施。

由 service 调用，不属于业务层，禁止 UI 直接调用。

## 原则

高内聚

低耦合

面向接口

禁止跨层调用。

## UI层

职责：

- 展示
- 用户交互

禁止：

数据库操作

禁止：

SSH连接

## Service层

职责：

业务逻辑

## Repository层

职责：

数据库访问

## SSH模块

职责：

- 建立连接
- 保持连接
- 关闭连接

禁止：

直接操作UI

## 传输库

SSH 传输：Apache Mina SSHD（client 模式）。

封装在 ssh 模块，对上仅暴露接口（见 API.md）。

## 终端模块

JavaFX 无内置终端控件。

不自研模拟内核，复用成熟方案。

选型：JediTerm（JetBrains 终端内核，IntelliJ IDEA 同款）。

理由：

- 成熟稳定，生产验证
- 内置 ANSI / VT100 / UTF-8 / 256色 / TrueColor / 回滚缓冲
- TtyConnector 抽象天然对接字节流

依赖：

- com.jediterm:jediterm-core
- com.jediterm:jediterm-ui

集成方式：

- ssh 模块：Mina SSHD ChannelShell（PTY）暴露 InputStream / OutputStream
- terminal 模块：实现 TtyConnector 包装上述流
- UI：JediTermWidget 经 SwingNode 嵌入 TerminalView

terminal 模块只做"适配"，不做"解析"。

> 备选（如需纯 JavaFX / 避免 Swing 互操作）：
> WebView + xterm.js，经 JS 桥写入字节流。
> 默认采用 JediTerm，备选不在 V1 实现。

## 安全

凭据（密码 / 私钥口令）加密后落库，见 DATABASE.md。

禁止明文落库，禁止密钥硬编码。

### 加密方案

主密码（Master Password）：

- 用户设置，不入库，不硬编码。
- 仅用于派生密钥，本身不存储。

密钥派生（KDF）：

- 算法：PBKDF2WithHmacSHA256（JDK 原生，无 native 依赖）
- 迭代：600000（参考 OWASP）
- 盐：16 字节随机，每库一份，存 settings
- 输出：256-bit 密钥

加密（AEAD）：

- 算法：AES-256-GCM
- IV：12 字节随机，每条凭据独立
- Tag：128-bit
- 落库格式：Base64( iv ‖ ciphertext ‖ tag )

主密码校验：

- 存一个验证块 verifier = AES-GCM 加密固定哨兵串。
- 解锁时派生密钥并解密 verifier，GCM 校验失败即主密码错误。
- 不存主密码、不存其哈希。

密钥生命周期：

- 派生密钥仅驻内存（会话期），锁定 / 退出即清除。
- 主密码修改：用旧密钥解密全部凭据，新密钥重新加密。

未设置主密码：

- 不持久化凭据，仅当次会话内存保留（见 DATABASE.md）。

> 选型理由：全部为 JDK 原生 javax.crypto，跨平台无 native 二进制。
> 如需更强 KDF，可后续切换 Argon2id（BouncyCastle 纯 Java 实现）。

## 国际化（i18n）

UI 文案统一走资源 ID + JSON 语言文件，详见 I18N.md。

模块归属：common.i18n。

要点：

- 所有界面文案禁止硬编码，经 I18n.t / I18n.tp 调用。
- 语言文件 messages_<locale>.json 放在 resources/i18n。
- 语言选择持久化到 settings（key = language），切换实时生效。
