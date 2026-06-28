# UI_DESIGN.md

# JFX SSH Client UI设计规范

---

# 文档目的

本文件用于统一项目UI设计风格。

所有Agent在开发界面前必须阅读本文件。

目标：

- 保持界面一致性
- 保持用户体验统一
- 避免UI风格混乱
- 避免重复设计

---

# 设计原则

## 整体风格

定位：

专业工具型软件

参考：

- XShell
- FinalShell
- MobaXterm
- VS Code
- JetBrains IDE

关键词：

- 简洁
- 高效
- 专业
- 现代化

禁止：

- 花哨动画
- 渐变背景
- 大面积圆角
- 游戏化风格

---

# 主题系统

必须支持：

## Light Theme

适用于：

白天办公环境

---

## Dark Theme

适用于：

夜间开发环境

---

主题切换后：

无需重启软件

实时生效

---

# 主窗口

使用：

BorderPane

布局：

```text
┌─────────────────────────────────────────────┐
│ MenuBar                                     │
├────────────┬────────────────────────────────┤
│            │                                │
│            │                                │
│ Connection │                                │
│ Tree       │ Terminal Tabs                  │
│            │                                │
│            │                                │
├────────────┴────────────────────────────────┤
│ Status Bar                                  │
└─────────────────────────────────────────────┘
```

---

# MenuBar

位置：

Top

结构：

```text
File

Connection

Tools

View

Help
```

---

## File

菜单：

```text
New Connection
Import
Export
Settings
Exit
```

---

## Connection

菜单：

```text
Connect

Disconnect

Reconnect

Close Session
```

---

## Tools

菜单：

```text
SFTP

Port Forward

Active Forwards
```

---

## View

菜单：

```text
Light Theme

Dark Theme

Reset Layout
```

---

## Help

菜单：

```text
About

Documentation
```

---

# 菜单可见性（最终形态）

Tools 菜单全部启用：

```text
Tools → SFTP             已启用
Tools → Port Forward     已启用
Tools → Active Forwards  已启用（查看 / 停止后台转发）
```

说明：

早期预留的 Plugin Manager（插件管理）不在产品需求内，菜单占位项已移除。

---

# 左侧导航区域

组件：

TreeView

名称：

ConnectionTree

宽度：

250px

允许调整大小

---

# 连接树结构

```text
Connections

├── Production
│    ├── Server01
│    └── Server02
│
├── Testing
│    ├── Test01
│    └── Test02
│
└── Local
```

---

# 连接树右键菜单

分组：

```text
Add Group
Rename Group
Delete Group
```

连接：

```text
Connect

Edit

Duplicate

Delete
```

---

# 主工作区

组件：

TabPane

名称：

TerminalTabs

---

# Tab规范

一个Tab对应：

一个SSH连接

结构：

```text
Tab

└── TerminalView
```

禁止：

多个SSH连接共享同一个Tab

---

# Tab标题

取值规则：

优先使用连接 name。

name 为空时，回退使用 host。

格式：

```text
name 或 host
```

示例：

```text
web01

db01

192.168.1.100
```

---

# Tab状态

连接成功：

```text
● web01
```

断开：

```text
○ web01
```

重连中：

```text
◐ web01
```

---

# Terminal区域

占据主工作区全部空间

---

# Terminal设计

支持：

- ANSI颜色
- UTF-8
- VT100
- 鼠标复制
- 粘贴

必须：

自动滚动

支持快捷键

---

# Terminal字体

优先：

Windows

```text
Consolas
```

Linux

```text
JetBrains Mono
```

macOS

```text
SF Mono
```

字体大小：

默认：

```text
14px
```

允许用户修改

范围：

```text
10~24
```

---

# 状态栏

位置：

Bottom

高度：

24px

---

# 状态栏显示内容

左侧：

```text
Connected
```

中间：

```text
UTF-8
```

右侧：

```text
SSH
```

示例：

```text
Connected | UTF-8 | SSH
```

---

# 新建连接窗口

形式：

Dialog

---

# 布局

GridPane

```text
Name

Host

Port

Username

Authentication
```

---

# 认证方式

下拉框：

```text
Password

Private Key
```

---

# Password模式

显示：

```text
Password
```

---

# Key模式

显示：

```text
Private Key Path
```

支持：

```text
Browse
```

按钮

---

# 高级设置区域

折叠面板

内容：

```text
KeepAlive

Connect Timeout

Read Timeout

Proxy
```

---

# 设置窗口

采用：

TabPane

---

# General

内容：

```text
Language

Theme

Auto Save
```

Language：

切换界面语言，实时生效、无需重启（见 I18N.md）。

---

# Terminal

内容：

```text
Font

Font Size

Cursor Style
```

---

# SSH

内容：

```text
KeepAlive

Timeout

Host Key Verify
```

---

# SFTP界面（V2）

布局：

```text
┌──────────────┬──────────────┐
│ Local        │ Remote       │
├──────────────┼──────────────┤
│ File List    │ File List    │
└──────────────┴──────────────┘
```

支持：

- 上传
- 下载
- 删除
- 重命名

---

# Port Forward界面（V3）

表格形式：

```text
Name

Local Port

Remote Host

Remote Port

Status
```

---

# Docker管理界面（V4）

布局：

```text
Container List

↓

Container Details

↓

Logs
```

---

# AI助手界面（V5）

位置：

右侧可折叠面板

宽度：

350px

结构：

```text
Question

↓

Chat Area

↓

Input Box
```

---

# 图标规范

统一使用：

Lucide Icons

或

FontAwesome

禁止混用多套图标库

---

# 间距规范

组件间距：

```text
8px
```

区域间距：

```text
12px
```

窗口边距：

```text
16px
```

---

# Agent开发要求

新增UI前：

必须检查本文件。

修改UI前：

必须确认是否符合本文件规范。

禁止：

擅自改变主布局结构。

禁止：

随意新增复杂导航模式。

禁止：

引入不一致的UI风格。

禁止：

硬编码界面文案（所有可见文字必须用资源 ID，见 I18N.md）。

所有新增界面必须遵循本规范。
