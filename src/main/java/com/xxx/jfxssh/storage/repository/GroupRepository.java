package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.storage.entity.Group;

import java.util.List;
import java.util.Optional;

/**
 * 分组数据访问接口（Repository 层，见 docs/ARCHITECTURE.md）。
 */
public interface GroupRepository {

    /**
     * 插入新分组，回填生成的主键。
     *
     * @param group 待插入分组
     * @return 带 id 的分组
     */
    Group insert(Group group);

    /**
     * 更新已有分组。
     *
     * @param group 待更新分组（须含 id）
     */
    void update(Group group);

    /**
     * 按 id 删除分组（子分组与连接由数据库外键级联处理）。
     *
     * @param id 主键
     */
    void delete(long id);

    /**
     * 按 id 查询。
     *
     * @param id 主键
     * @return 分组，不存在时为空
     */
    Optional<Group> findById(long id);

    /**
     * 查询全部分组。
     *
     * @return 分组列表
     */
    List<Group> findAll();

    /**
     * 按父分组查询；{@code parentId} 为 null 时查询根分组。
     *
     * @param parentId 父分组 id 或 null
     * @return 分组列表
     */
    List<Group> findByParent(Long parentId);
}
