package com.xxx.jfxssh.ssh;

/**
 * 一条已启动的端口转发句柄。
 *
 * <p>由 {@link SshSession#openForward(PortForwardSpec)} 创建；{@link #close()} 停止转发
 * （不关闭其依附的 {@link SshSession}）。</p>
 */
public interface PortForward extends AutoCloseable {

    /** @return 规则名 */
    String name();

    /** @return 转发类型 */
    PortForwardSpec.Type type();

    /** @return 实际绑定端口（绑定端口为 0 时为系统分配的临时端口） */
    int boundPort();

    /** @return 转发是否仍存活 */
    boolean isOpen();

    /** 停止转发（幂等）。 */
    @Override
    void close();
}
