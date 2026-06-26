# 内存占用专项审计报告（JFX SSH Client）

> 日期：2026-06-26 ｜ 适用版本：含 commit `3f2fe71` 起的内存优化
> 现象：macOS 下进程 RSS 可达 1GB+，而 JVM Heap 仅约 100~200MB（`GC.heap_info` 已验证），
> 运行 `claude` / 长文本输出后明显飙升，疑似与终端组件相关。

---

## 结论（最可能原因）

**这是 Off-Heap（堆外）问题，不是 Heap 泄漏，也不是当前的 SceneGraph 泄漏。**

关键证据：堆仅 100~200MB，而 RSS 1GB+ → 多出来的 800MB+ 在堆外；且增长与**终端吞吐量**强相关。
三个堆外来源，按贡献排序：

1. **JDK NIO 每线程临时直接内存缓存（`sun.nio.ch.Util`）—— 头号元凶。**
   Mina SSHD 用 Nio2（异步 socket），每个 I/O 线程会缓存一块"临时直接 ByteBuffer"用于读
   socket，大小会涨到历史最大读取量并**永不释放**（线程本地复用）。高速输出时被撑大且常驻。
2. **NIO DirectByteBuffer（socket 读）。** 堆外，由 Cleaner 在 GC 时释放；堆很小 → GC 很少
   触发 → 直接内存堆积。
3. **Java2D / AWT 原生渲染（SwingNode）。** JediTerm 默认 50fps，长输出时持续重绘；SwingNode
   每帧把整个 Swing 面板抓成 BufferedImage 再上传为 JavaFX 纹理 → 原生像素缓冲 + 字形缓存
   随渲染量增长。

---

## 内存增长路径分析

```
claude 流式输出
   → SSH socket 高速到达
   → Mina Nio2 读 socket：sun.nio.ch.Util 线程本地直接缓冲被撑大且常驻   ← 堆外 ①
   → DirectByteBuffer 持续分配，堆小→GC少→Cleaner不跑→堆外堆积           ← 堆外 ②
   → channel.getInvertedOut() 管道（受 SSH 窗口流控，有界）
   → SshTtyConnector.read(char[]) / InputStreamReader(8KB)（有界）        ← 业务代码无泄漏
   → JediTerm 解析进 TextBuffer（堆，受 scrollback 上限约束）
   → TerminalPanel 50fps 重绘 → SwingNode 抓图+上传纹理（Java2D 原生）    ← 堆外 ③
```

---

## 问题代码位置 / 逐项核查

| 检查项 | 结论 | 位置 |
|---|---|---|
| ① 堆外增长来源 | NIO 临时缓存 + DirectByteBuffer + Java2D，**均非业务代码，在 JDK/Mina/AWT 层** | — |
| ② 终端缓冲无限增长 | **曾经是**：JediTerm 默认 `getBufferMaxLinesCount=5000`；已覆盖为 **2000** | `terminal/ThemedTerminalSettings` |
| ③ SSH 流无限缓存 | **否**。8KB `InputStreamReader`，`read(char[])` 按需读；Mina 管道受 SSH 窗口流控 | `terminal/SshTtyConnector`、`ssh/MinaSshShell` |
| ④ JavaFX 节点频繁创建/未释放 | **曾经有**：连接树单元格每次 `updateItem` 新建 ContextMenu + `I18n.tp` 绑定（强引用静态 locale，永不释放）→ 已修（菜单每格只建一次） | `ui/tree/ConnectionTreeView`（已修） |
| ⑤ 线程 / Executor 泄漏 | **否**。每终端：worker（短命）、watchClose（daemon，shell 关即退出）；`releaseSession` 关 connector/session/widget；`widget.close()→stop()` 关 JediTerm 内部 executor | `ui/terminal/TerminalTabsPane.releaseSession/watchClose` |
| ⑥ DirectByteBuffer 未释放 | **是（根因）**：JDK NIO 线程本地直接缓存 + Cleaner 不及时 | JDK NIO / Mina Nio2 |
| ⑦ JediTerm history size | 默认 5000 偏大 + 50fps 重绘；已收紧（2000 + 30fps） | `terminal/ThemedTerminalSettings` |

---

## 必答问题

**1. 内存增长来源判断**
- Off-Heap（堆 100~200MB，RSS 1GB+）。
- **存在 DirectByteBuffer / NIO 临时缓存堆积。**
- **当前无 JavaFX SceneGraph 泄漏**（树单元格那处已修，堆稳定在百 MB 级，印证非节点泄漏）。

**2. Terminal 设计问题**
- 曾有无限 scrollback（默认 5000，已设 2000）。
- **非逐字符渲染**（JediTerm 把整缓冲绘到一个 Swing 面板，不创建 per-char 节点）。
- **不会每次输出新建 String/Node。**
- ANSI 解析是 JediTerm 自身、效率可控，无对象爆炸。真正开销是 50fps 重绘 → SwingNode 原生抓图。

**3. SSH 流问题**
- InputStream **不无限缓存**（8KB reader + SSH 窗口流控）。
- Channel / Session **关闭时已释放**。
- 后台线程**有界且会退出**。

**4. UI 问题**
- Tab 关闭**已释放**（`closeCard → releaseSession` 关 widget，并 `showContent` 解除 SwingNode 引用）。
- 强引用导致 GC 回收不了的那处（I18n.tp 树菜单）**已修**。

---

## 修复方案（逐条，均已实施）

1. **封顶 JDK NIO 线程本地直接缓存（最关键）**：`-Djdk.nio.maxCachedBufferSize=262144`（256KB）。
   超过此大小的临时直接缓冲用完即释放，不再被线程永久缓存。
2. **封顶 NIO 直接内存**：`-XX:MaxDirectMemorySize=128m`，到顶即触发引用处理 GC 释放 DirectByteBuffer。
3. **空闲周期 GC 还内存给系统**：`-XX:G1PeriodicGCInterval=10000`，每 10s 跑 GC，既跑 Cleaner
   释放堆外，又让 G1 uncommit 堆 → 用完会**回落**。
4. **限制堆**：`-Xmx512m`（+ `-XX:+UseStringDeduplication`）。
5. **终端缓冲限制策略**：`getBufferMaxLinesCount()=2000`（回滚历史上限，堆内有界）。
6. **终端输出优化策略**：`maxRefreshRate() 50→30`（降重绘频率 40%，直接减少 SwingNode/Java2D
   原生纹理 churn）。
7. **线程释放策略**：维持现状已正确——`releaseSession` 关 connector→session→`widget.close()`
   （停 JediTerm executor）；watchClose 为 daemon 且随 shell 关闭退出；重连先 release 再连。

> 启动参数已写进 `run-mac.command` / `mvn exec` / `docs/RUN_MACOS.md`。
> **务必带参数运行**（裸 `java -jar` 会用默认 1/4 RAM 大堆）。

### 当前启动参数（完整）

```
-Xmx512m
-XX:MaxDirectMemorySize=128m
-Djdk.nio.maxCachedBufferSize=262144
-XX:+UseStringDeduplication
-XX:G1PeriodicGCInterval=10000
--add-modules javafx.controls,javafx.swing
```

---

## 推荐架构调整（可选）

- **短期（已做）**：上面 6+7 条，预计 macOS 上长输出后 RSS 稳定在 ~300–500MB 且能回落。
- **中期**：若仍嫌高，可在我们这层加一道**输出节流 / 合并**（连续高频小包合并后再喂 JediTerm），
  进一步降重绘次数；并把 scrollback / 刷新率做成设置项。
- **根治（大改）**：SwingNode 嵌 JediTerm 是堆外开销与 macOS IME 问题的共同根源。彻底方案是
  **换成 JavaFX 原生终端渲染**（Canvas 自绘 + 自接 ANSI 解析，或找 JavaFX 原生终端库），去掉
  SwingNode 的"抓图上传"开销，同时顺便解决中文输入法。代价是重写终端渲染层，建议作为 V2 评估项。

---

## 复测与进一步定位

```bash
# 重新打包
mvn -DskipTests package

# 用带参数方式启动（或直接用 run-mac.command）
java -Xmx512m -XX:MaxDirectMemorySize=128m -Djdk.nio.maxCachedBufferSize=262144 \
  -XX:+UseStringDeduplication -XX:G1PeriodicGCInterval=10000 \
  --add-modules javafx.controls,javafx.swing -jar jfxssh.jar
```

跑 `claude` 输出一大段后看活动监视器：RSS 应明显更低、且**输出停止后回落**。

若带齐参数仍异常，开启 NMT 精确定位堆外分项：

```bash
# 启动加： -XX:NativeMemoryTracking=summary
jcmd <pid> VM.native_memory summary        # 看 Internal / Other（直接内存）/ Thread / Code
jcmd <pid> GC.heap_info                     # 确认堆是否真满
```

- `Internal` / `Other` 偏大 → 直接内存 / NIO 缓存（继续调小 `MaxDirectMemorySize` 或 `maxCachedBufferSize`）。
- `Thread` 偏大 → 线程数过多（检查是否有未退出的 watcher / reader 线程）。
- 堆其实是满的 → 回到 Heap 排查（`jmap -dump:live` + MAT 看 dominator）。
