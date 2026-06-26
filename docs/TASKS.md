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

- **V2 SFTP**：进行中（首版已交付）。
  - 首版（DONE）：独立窗口的 SFTP 文件浏览器，每连接一个 `Stage`；列出远程文件
    （名称/大小/类型/修改时间，点列头排序、默认目录优先），地址栏 + 上级 + 刷新，
    双击目录进入、双击文件下载到本地。SSH 层加 `SftpSession`/`MinaSftpSession` +
    `SshSession.openSftp()`（Mina sshd-sftp）；UI 层 `ui/sftp`（SftpLauncher /
    SftpBrowserLauncher / SftpBrowserWindow）；右键连接 → SFTP、Tools → SFTP（已启用）。
    单线程执行器串行化 SFTP 调用，关窗释放 SFTP+SSH。测试：EmbeddedSshServer 加 SFTP
    子系统 + 虚拟根，MinaSftpSessionTest（列目录 + 下载字节）。
  - 待办：上传、删除、重命名、新建目录、下载进度、本地/远程双栏。
- **V3 Port Forward**：端口转发表格管理；Tools → Port Forward 菜单已置灰预留
- **V4 Docker**：容器列表 / 详情 / 日志
- **V5 AI 助手**：右侧可折叠聊天面板
- 插件系统（Plugin Manager）：产品需求 V1–V5 之外，仅 UI 预留，暂不实现
