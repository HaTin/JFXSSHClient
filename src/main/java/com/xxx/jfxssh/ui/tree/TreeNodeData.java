package com.xxx.jfxssh.ui.tree;

import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.Group;

/**
 * 连接树节点数据。
 *
 * <p>承载节点类型与对应实体：根 / 分组 / 连接。右键菜单按类型分发。</p>
 */
public final class TreeNodeData {

    /** 节点类型。 */
    public enum Type {
        /** 根节点。 */
        ROOT,
        /** 分组节点。 */
        GROUP,
        /** 连接节点。 */
        CONNECTION
    }

    private final Type type;
    private final Group group;
    private final Connection connection;
    private final String label;

    private TreeNodeData(Type type, Group group, Connection connection, String label) {
        this.type = type;
        this.group = group;
        this.connection = connection;
        this.label = label;
    }

    /**
     * @param label 根标签
     * @return 根节点数据
     */
    public static TreeNodeData root(String label) {
        return new TreeNodeData(Type.ROOT, null, null, label);
    }

    /**
     * @param group 分组
     * @return 分组节点数据
     */
    public static TreeNodeData group(Group group) {
        return new TreeNodeData(Type.GROUP, group, null, null);
    }

    /**
     * @param connection 连接
     * @return 连接节点数据
     */
    public static TreeNodeData connection(Connection connection) {
        return new TreeNodeData(Type.CONNECTION, null, connection, null);
    }

    /** @return 节点类型 */
    public Type type() {
        return type;
    }

    /** @return 分组（仅 GROUP 节点非空） */
    public Group group() {
        return group;
    }

    /** @return 连接（仅 CONNECTION 节点非空） */
    public Connection connection() {
        return connection;
    }

    /** @return 展示名称 */
    public String displayName() {
        switch (type) {
            case GROUP:
                return group.getName();
            case CONNECTION:
                return connection.getName() != null && !connection.getName().isBlank()
                        ? connection.getName() : connection.getHost();
            default:
                return label;
        }
    }

    @Override
    public String toString() {
        return displayName();
    }
}
