package com.xxx.jfxssh.service;

import com.xxx.jfxssh.storage.entity.Group;

import java.util.ArrayList;
import java.util.List;

/**
 * 分组树节点（供 UI 连接树展示，见 docs/API.md GroupService.findTree）。
 *
 * <p>包装一个 {@link Group} 及其子节点。</p>
 */
public final class GroupNode {

    private final Group group;
    private final List<GroupNode> children = new ArrayList<>();

    /**
     * @param group 分组
     */
    public GroupNode(Group group) {
        this.group = group;
    }

    /** @return 当前分组 */
    public Group getGroup() {
        return group;
    }

    /** @return 子节点（可变，供构建时填充） */
    public List<GroupNode> getChildren() {
        return children;
    }
}
