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
