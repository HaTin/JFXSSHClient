package com.xxx.jfxssh.service;

import com.xxx.jfxssh.storage.entity.Group;

import java.util.List;
import java.util.Optional;

/**
 * 分组管理业务接口（Service 层，见 docs/API.md、docs/ARCHITECTURE.md）。
 *
 * <p>承载分组的增删改查与树构建。UI 禁止直接访问 Repository / 数据库。</p>
 */
public interface GroupService {

    /**
     * 新增分组。
     *
     * @param group 待保存分组
     * @return 带 id 的分组
     */
    Group save(Group group);

    /**
     * 重命名分组。
     *
     * @param id   分组 id
     * @param name 新名称
     * @return 更新后的分组
     */
    Group rename(long id, String name);

    /**
     * 删除分组（子分组级联删除，连接的 group_id 置空）。
     *
     * @param id 分组 id
     */
    void delete(long id);

    /**
     * 按 id 查询。
     *
     * @param id 分组 id
     * @return 分组，不存在时为空
     */
    Optional<Group> findById(long id);

    /**
     * 查询全部分组（平铺）。
     *
     * @return 分组列表
     */
    List<Group> findAll();

    /**
     * 构建分组树。
     *
     * @return 根节点列表
     */
    List<GroupNode> findTree();
}
