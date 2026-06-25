/**
 * 终端模块。
 *
 * <p>以 JediTerm 作为终端内核，{@link com.xxx.jfxssh.terminal.SshTtyConnector}
 * 把 SSH shell 字节流适配为 TtyConnector，{@link com.xxx.jfxssh.terminal.TerminalView}
 * 经 SwingNode 把 JediTerm 控件嵌入 JavaFX（见 docs/ARCHITECTURE.md）。
 * 只做适配与承载，不做协议解析。</p>
 */
package com.xxx.jfxssh.terminal;
