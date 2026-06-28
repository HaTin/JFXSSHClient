# 数据库设计

SQLite

## 通用约定

- 主键：id（INTEGER，自增）
- 时间：create_time / update_time（TEXT，ISO-8601）
- 外键：开启 PRAGMA foreign_keys = ON

---

## groups

连接分组，支持树形嵌套（对应连接树 Production / Testing / Local）。

字段：

id

name

parent_id        -- 父分组，根分组为 NULL，外键 groups(id)

sort             -- 同级排序

create_time

update_time

---

## connections

字段：

id

name

host

port

username

auth_type        -- 枚举：PASSWORD / PRIVATE_KEY

password_enc     -- 加密后的密码，仅 auth_type=PASSWORD 时使用，可空

private_key_path -- 旧版兜底：私钥文件路径，仅在无 private_key_enc 时回退读取，可空

private_key_enc  -- 私钥内容密文（AES-256-GCM），auth_type=PRIVATE_KEY 时使用，可空

passphrase_enc   -- 私钥口令密文（同一加密方案），可空

group_id         -- 所属分组，外键 groups(id)，可空（未分组）

remark

create_time

update_time

---

### 密码存储策略

禁止明文存储密码（见 CODING_STANDARDS）。

加密方案（算法 / 主密码 / 生命周期）见 ARCHITECTURE.md。

规则：

- password_enc 仅存密文，格式 Base64( iv ‖ ciphertext ‖ tag )（AES-256-GCM）。
- 加密密钥由主密码经 PBKDF2 派生，不入库、不硬编码。
- 未设置主密码时，不持久化密码，仅当次会话内存保留。
- 私钥内容与私钥口令采用同一方案加密存储（private_key_enc / passphrase_enc）；旧库
  的 private_key_path 在无密文内容时作为兜底，连接时直接读取该文件。

KDF 参数与校验块存于 settings：

```text
security.kdf            = PBKDF2WithHmacSHA256
security.kdf.iterations = 600000
security.kdf.salt       = base64(16 bytes)
security.verifier       = base64(iv‖ciphertext‖tag)   -- 主密码校验哨兵
```

---

## port_forwards

端口转发规则，按连接保存（删除连接时级联删除）。打开窗口时加载但不自动启动；标记 auto_start=1 的规则在终端 SSH 连接成功后会自动启动。

字段：

id

connection_id    -- 所属连接，外键 connections(id) ON DELETE CASCADE

name             -- 规则名

type             -- 枚举：LOCAL / REMOTE / DYNAMIC

bind_host        -- 绑定主机

bind_port        -- 绑定端口（0 表示由系统分配临时端口）

dest_host        -- 目标主机（DYNAMIC 类型可为 NULL）

dest_port        -- 目标端口（DYNAMIC 类型可为 0/NULL）

auto_start       -- 0=false, 1=true；终端 SSH 连接成功后自动启动该规则

create_time

update_time

---

## settings

id

key

value

create_time

update_time

---

## schema_version

数据库版本，用于后续迁移。

id

version          -- 当前 schema 版本号

applied_time
