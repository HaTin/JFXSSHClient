package com.xxx.jfxssh.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.xxx.jfxssh.ssh.SshShell;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 将 {@link SshShell} 适配为 JediTerm 的 {@link TtyConnector}。
 *
 * <p>只做字节流到终端内核的桥接：读取服务器输出、写入按键、转发尺寸变更；
 * ANSI/VT100 解析与渲染由 JediTerm 负责。</p>
 */
public final class SshTtyConnector implements TtyConnector {

    private final SshShell shell;
    private final String name;
    private final InputStreamReader reader;
    private final OutputStream output;

    /**
     * @param shell SSH shell 通道
     * @param name  显示名称
     */
    public SshTtyConnector(SshShell shell, String name) {
        this.shell = shell;
        this.name = name;
        this.reader = new InputStreamReader(shell.getInputStream(), StandardCharsets.UTF_8);
        this.output = shell.getOutputStream();
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        output.write(bytes);
        output.flush();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return shell.isOpen();
    }

    @Override
    public void resize(TermSize termSize) {
        shell.resize(termSize.getColumns(), termSize.getRows());
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
