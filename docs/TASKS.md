# 当前任务

状态：

V1 全部任务（0–8）已完成；进入 V1.1 增强与维护阶段。后续大版本（V2 SFTP /
V3 Port Forward / V4 Docker / V5 AI）尚未启动。

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

DONE（AppConfig 基于 properties 文件 load/save/set，语言/主题/字体/字号/回滚行数/
KeepAlive/Timeout/主机密钥校验等设置项均落盘并启动恢复；SettingsService 负责加密相关
KDF 参数与凭据存于 settings 表）

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
- 任务5：配置持久化（AppConfig properties + settings 表）
- 任务6：主题切换（Light / Dark 实时）
- 任务7：凭据加密（主密码 + PBKDF2 + AES-256-GCM）
- 任务8：多语言（i18n）

---

## 工程化

- 单元测试：JUnit 5 + surefire，53 个用例（cipher / vault / settings / i18n /
  connection / group + 级联 / ssh 集成 / tty-connector 断开重连 / SFTP 浏览·传输·
  取消·多通道·错误码 / 端口转发本地+动态），mvn test 全绿（其中权限错误用例在 root 环境自动跳过）

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
- 导入 / 导出连接：File → Export/Import，JSON 格式（连接 + 分组结构）；**不导出密码**
  （密文绑定本机主密码、换机无法解密，明文不安全；导出前弹确认说明）；导入按
  名称+父分组 / 名称|主机|端口|用户名 去重合并，幂等
- 连接成功后提示保存凭据：当凭据为本次手动输入（导入的无密码连接 / 临时改用其它认证），
  连接成功后弹窗询问是否保存到该连接（密码经主密码加密落库，私钥保存路径），下次免输；
  仅在连接成功时提示，且仅对手动输入的凭据（已存并解密成功的不重复提示）
- 菜单接线：Connection 菜单（Connect 连接选中项 / Disconnect / Reconnect / Close
  Session 作用于活动终端）；View → Reset Layout 复位分隔条；Help → About（简介 +
  版本 + 开发者 @HaTin）/ Documentation

---

## 已知限制 / 待办

- **私钥口令（passphrase）不持久化**：connections 表与 Connection 实体仅有
  password_enc / private_key_path，无 passphrase 字段。带口令保护的私钥即便"保存凭据"
  也只存路径，重连时 buildConfig 不会再提示输入口令 → 认证会失败。与 DATABASE.md
  "私钥口令采用同一方案存储"不符。修复需：schema 加 passphrase_enc 列（v3 迁移）+
  实体/Repository 读写 + 保存时加密 + buildConfig 解密回填，解密失败再补输。
- **光标样式设置**：设置窗口 Terminal 页已有下拉但**置灰**，JediTerm 暂无对应设置钩子，预留。
- **一次性命令执行 execute()**：API.md 标记为 V1 可选，当前未实现（仅交互式 openShell）。

---

## 后续版本（产品需求 V2–V5）

- **V2 SFTP**：进行中（双栏文件管理器已交付，含并发传输与进度/取消）。
  - 首版（DONE）：独立窗口的 SFTP 文件浏览器，列出远程文件、列排序、双击进入/下载。
  - 双栏（DONE）：左本地（`LocalPane`，java.nio）+ 右远程（`RemotePane`）。**上传 / 下载放入
    各栏右键菜单**（左栏「上传到远程」、右栏「下载到本地」），不再用中间独立按钮。
    两栏均支持新建文件夹 / 重命名 / 删除（远程递归删除）。
  - 传输（DONE）：**每个传输独立开一条 SFTP 通道、独立线程**——传输不阻塞浏览，且可
    **并发多个**。底部为传输列表，每行显示进度条 + **百分比 + 已传/总 MB + 速度** + **取消按钮**。
    取消 / 关窗中断会清理半成品（远程残file remove、本地残file delete）且不误报错误。
  - 错误处理（DONE）：`SftpOperationException` 带 SFTP 状态码；`SftpErrors` 将其与本地
    java.nio 异常翻译为具体原因（权限不足 / 不存在 / 已存在 / 目录非空 / 不支持），
    失败弹「上传 X 失败：权限不足」并复位状态栏为「失败：X」。
  - SSH 层：`SftpSession`/`MinaSftpSession` + `SshSession.openSftp()`（sshd-sftp）；
    `SftpProgress` 进度回调、`upload/download(带进度+取消)/mkdir/rename/delete(递归)`。
  - 接线：右键连接 → SFTP、Tools → SFTP（已启用）。
  - 测试：MinaSftpSessionTest 覆盖 list / download / upload(进度) / 取消清理 /
    多通道共存 / mkdir+rename+递归delete / 权限错误状态码（root 环境自动跳过）。
  - 待办：**文件夹整体上传/下载**（当前选目录提示"请选择文件"）、**覆盖确认**（同名直接覆盖）、
    **取消单个传输后目标栏不自动刷新**、断点续传、传输队列（并发数上限/暂停/重排）、拖拽。
- **V3 Port Forward**：已完成。
  - 支持本地转发（-L）、远程转发（-R）、动态 SOCKS 代理（-D）。
  - 独立窗口管理规则：表格展示名称/类型/绑定/目标/状态；工具栏添加/启动/停止/编辑/移除/全部启动。
  - **规则持久化**：按连接保存到 SQLite（port_forwards 表），关窗不丢失；打开窗口时加载但不自动启动。
  - **后台运行**：已启动的转发在关闭 Port Forward 窗口后继续运行，由 `ActivePortForwardService` 持有 SSH 会话与句柄；`Tools → Active Forwards` 可查看并停止后台转发。
  - **自动启动**：规则可标记 `auto_start`，终端 SSH 连接成功后自动启动对应规则。
  - 接线：右键连接 → Port Forward、Tools → Port Forward。
  - 测试：`MinaPortForwardTest` 覆盖本地转发端到端隧道、动态转发开启/关闭；
    `PortForwardServiceTest` / `PortForwardRepositoryTest` 覆盖持久化 CRUD 与外键级联删除；
    `ActivePortForwardServiceTest` 覆盖后台启动/停止/全部停止。
  - 待办：批量导入/导出、远程转发集成测试。
- **V4 Docker**：容器列表 / 详情 / 日志
- **V5 AI 助手**：右侧可折叠聊天面板
- 插件系统（Plugin Manager）：产品需求 V1–V5 之外，仅 UI 预留，暂不实现
