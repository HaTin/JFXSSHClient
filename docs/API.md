# Service接口规范

分层：UI → Service → Repository → Database

SSH 传输由 ssh 模块封装，仅供 Service 调用，禁止 UI 直接调用。

---

## 概念模型

Session：一个 SSH 会话，对应一个终端 Tab。

一个 Session 持有一条交互式 shell channel（PTY），提供输入 / 输出流。

SSHService：负责传输层（建立 / 认证 / 打开 shell / 关闭）。

SessionManager：负责会话生命周期与多 Tab 管理。

---

## SSHService

connect(Connection)        -- 建立并认证连接，支持密码 / 公钥

openShell(...)             -- 打开交互式 shell channel（PTY），返回输入输出流

disconnect()               -- 关闭连接

execute(command)           -- 一次性命令执行（非交互场景）；最终形态未实现，保留约定供未来扩展

说明：

终端为交互式，使用 openShell；execute 为预留的非交互入口，当前版本不提供实现。

---

## SessionManager

createSession(Connection)  -- 创建会话（内部调用 SSHService），返回 Session

closeSession(sessionId)

getSession(sessionId)

listSessions()

---

## ConnectionService

save(Connection)

update(Connection)

delete(id)

findAll()

findByGroup(groupId)

---

## GroupService

save(Group)

rename(id, name)

delete(id)

findTree()                 -- 返回分组树（供连接树展示）

---

## SettingsService

get(key)

set(key, value)

getAll()
