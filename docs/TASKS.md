# 当前任务

状态：

开发中

---

## Milestone V1

### 任务0

项目基础骨架（Maven / 模块包 / 入口 / 主窗口框架 / 日志 / 配置 / DB 初始化）

状态：

DONE

---

### 任务1

实现主窗口

状态：

DONE（BorderPane + MenuBar + ConnectionTree + TerminalTabs + StatusBar，含连接树右键菜单）

---

### 任务2

实现连接管理

状态：

DONE（后端 + UI：连接树加载真实数据，新建/编辑/删除/复制连接对话框）

---

### 任务2.1

实现分组管理（groups 表 / 连接树 / 右键增删改）

状态：

DONE（后端 + UI：连接树按分组嵌套展示，右键添加/重命名/删除分组）

---

### 任务3

实现SSH连接

状态：

DONE（SSH 连接管理：Mina SSHD，密码 + 公钥认证、保活、关闭；shell/终端不含，属任务4）

---

### 任务4

实现多标签页

状态：

DONE（JediTerm 终端经 SwingNode 嵌入；SSH shell channel；一连接一 Tab，状态点 ◐●○；连接打开终端）

---

### 任务5

实现配置持久化

状态：

TODO

---

### 任务6

实现主题切换（Light / Dark，实时生效，无需重启）

状态：

DONE（ThemeManager 实时切换 + 持久化，View 菜单已接）

---

### 任务7

实现凭据加密（主密码 + PBKDF2 + AES-256-GCM，见 ARCHITECTURE.md）

状态：

DONE（CredentialCipher + CredentialVault + SettingsService；保存连接时加密、连接时解密）

---

### 任务8

实现多语言（资源 ID + JSON 语言文件 + 实时切换，见 I18N.md）

状态：

DONE

---

## 设计已定稿

- 终端模拟：JediTerm
- 凭据加密：主密码 + PBKDF2-HMAC-SHA256 + AES-256-GCM
- 多语言：资源 ID + messages_<locale>.json + I18n 工具（Jackson 解析）

---

## 已完成

- 任务0：项目基础骨架
- 任务1：主窗口框架
- 任务2：连接管理（Service / Repository / 实体 + UI 接入）
- 任务2.1：分组管理（Group 后端 + 连接树 UI 接入）
- 任务3：SSH 连接管理（Mina SSHD，密码 + 公钥）
- 任务4：多标签页 + 终端（JediTerm + SSH shell）
- 任务6：主题切换（Light / Dark 实时）
- 任务7：凭据加密（主密码 + PBKDF2 + AES-256-GCM）
- 任务8：多语言（i18n）

---

## 工程化

- 单元测试：JUnit 5 + surefire，31 个用例（cipher / vault / settings / i18n /
  connection / group + 级联 / ssh 集成 / tty-connector 断开重连），mvn test 全绿

---

## 增强（V1.1）

- 设置窗口：File → Settings，TabPane General / Terminal / SSH
  - General：语言（已从 View 菜单归位）、主题、自动保存（实时生效）
  - Terminal：字体、字号（**立即应用到所有已打开终端**，JediTerm reinitFontAndResize）；
    回滚行数（新终端生效，默认 10000）；光标样式预留（置灰）
  - SSH：KeepAlive、Timeout（对新连接生效）；主机密钥校验（已做实，默认开启）
- 终端类型（PTY type）：新建/编辑连接可选 xterm-256color / xterm / vt100 / vt220 /
  ansi / linux / screen，作为每连接选项落库；connections 表加 terminal_type 列
  （schema v2，含老库 ALTER 迁移）
- 键盘菜单：菜单栏「键盘」一键向活动终端发送控制序列——控制键（Ctrl+C/D/Z/L/A/E/
  U/K/W/R/G/\）、特殊键（Tab/Esc/Enter/Backspace/Del/Ins/Home/End/PgUp/PgDn）、
  方向键、功能键 F1–F12
- 主机密钥校验（known_hosts，TOFU）：首次连接记录指纹；再次连接指纹不一致时弹
  红色警告（列已记录/本次指纹），「仍然继续」按钮倒计时 5 秒后才可点，可取消或继续
  （继续则更新记录）；设置项「主机密钥校验」开关控制，默认开启
- macOS 中文输入：终端底部加 JavaFX 输入框（输入法正常，支持中文），回车发送到活动
  终端、Shift+回车换行；内容超一行向上浮层展开，不改变终端大小（规避 SwingNode 在
  macOS 上 IME 失效的问题）
- 菜单接线：Connection 菜单（Connect 连接选中项 / Disconnect / Reconnect / Close
  Session 作用于活动终端）；View → Reset Layout 复位分隔条；Help → About（简介 +
  版本 + 开发者 @HaTin）/ Documentation
