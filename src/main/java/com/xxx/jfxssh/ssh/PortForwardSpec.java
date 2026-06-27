package com.xxx.jfxssh.ssh;

/**
 * 一条端口转发规则（不可变）。
 *
 * <p>三种类型字段语义：</p>
 * <ul>
 *   <li><b>LOCAL</b>：{@code bindHost:bindPort} 为本地监听地址，{@code destHost:destPort}
 *       为经 SSH 服务器到达的目标。</li>
 *   <li><b>REMOTE</b>：{@code bindHost:bindPort} 为服务器端监听地址，{@code destHost:destPort}
 *       为客户端可达的目标。</li>
 *   <li><b>DYNAMIC</b>：{@code bindHost:bindPort} 为本地 SOCKS 代理监听地址，{@code dest*} 不用。</li>
 * </ul>
 *
 * <p>{@code bindPort} 为 0 时由系统分配临时端口（实际端口见 {@link PortForward#boundPort()}）。</p>
 *
 * @param name       规则名（仅展示）
 * @param type       转发类型
 * @param bindHost   绑定主机
 * @param bindPort   绑定端口（0=临时）
 * @param destHost   目标主机（DYNAMIC 忽略）
 * @param destPort   目标端口（DYNAMIC 忽略）
 * @param autoStart  连接成功后是否自动启动（默认 false）
 */
public record PortForwardSpec(
        String name,
        Type type,
        String bindHost,
        int bindPort,
        String destHost,
        int destPort,
        boolean autoStart) {

    /**
     * 构造一条默认不自动启动的规则。
     *
     * @param name     规则名
     * @param type     转发类型
     * @param bindHost 绑定主机
     * @param bindPort 绑定端口
     * @param destHost 目标主机
     * @param destPort 目标端口
     */
    public PortForwardSpec(String name, Type type, String bindHost, int bindPort,
                           String destHost, int destPort) {
        this(name, type, bindHost, bindPort, destHost, destPort, false);
    }

    /** 转发类型。 */
    public enum Type {
        /** 本地转发（-L）。 */
        LOCAL,
        /** 远程转发（-R）。 */
        REMOTE,
        /** 动态转发 / SOCKS（-D）。 */
        DYNAMIC
    }
}
