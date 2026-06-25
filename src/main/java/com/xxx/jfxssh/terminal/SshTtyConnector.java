package com.xxx.jfxssh.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.xxx.jfxssh.ssh.SshShell;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 {@link SshShell} 适配为 JediTerm 的 {@link TtyConnector}。
 *
 * <p>只做字节流到终端内核的桥接：读取服务器输出、写入按键、转发尺寸变更；
 * ANSI/VT100 解析与渲染由 JediTerm 负责。</p>
 *
 * <p>会话断开后 JediTerm 仍可能投递按键：此时写入静默丢弃，避免向已关闭通道
 * 写入而报错；若按下回车，则触发重连回调（见 docs/UI_DESIGN.md 终端行为）。</p>
 */
public final class SshTtyConnector implements TtyConnector {

    private final SshShell shell;
    private final String name;
    private final InputStreamReader reader;
    private final OutputStream output;
    private final Runnable onReconnect;
    private final AtomicBoolean reconnectRequested = new AtomicBoolean(false);

    /**
     * @param shell       SSH shell 通道
     * @param name        显示名称
     * @param onReconnect 断开后按回车触发的重连回调（可空）
     */
    public SshTtyConnector(SshShell shell, String name, Runnable onReconnect) {
        this.shell = shell;
        this.name = name;
        this.onReconnect = onReconnect;
        this.reader = new InputStreamReader(shell.getInputStream(), StandardCharsets.UTF_8);
        this.output = shell.getOutputStream();
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public synchronized void write(byte[] bytes) throws IOException {
        if (!shell.isOpen()) {
            maybeReconnect(bytes);
            return;
        }
        try {
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            // 通道在写入瞬间关闭：按断开处理，不上抛（否则 JediTerm 会刷栈）
            maybeReconnect(bytes);
        }
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    private void maybeReconnect(byte[] bytes) {
        if (onReconnect != null && containsEnter(bytes)
                && reconnectRequested.compareAndSet(false, true)) {
            onReconnect.run();
        }
    }

    private boolean containsEnter(byte[] bytes) {
        for (byte b : bytes) {
            if (b == '\r' || b == '\n') {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        return shell.isOpen();
    }

    @Override
    public void resize(TermSize termSize) {
        if (shell.isOpen()) {
            shell.resize(termSize.getColumns(), termSize.getRows());
        }
    }

    @Override
    public int waitFor() {
        return shell.waitForClose();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() {
        shell.close();
    }
}
