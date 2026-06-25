package com.xxx.jfxssh.service;

import com.xxx.jfxssh.storage.entity.Connection;

import java.util.List;
import java.util.Optional;

/**
 * 连接管理业务接口（Service 层，见 docs/API.md、docs/ARCHITECTURE.md）。
 *
 * <p>承载连接的增删改查与校验，向上供 UI 调用，向下依赖 Repository。
 * UI 禁止直接访问 Repository / 数据库。</p>
 */
public interface ConnectionService {

    /**
     * 新增连接（生成时间戳并持久化）。
     *
     * @param connection 待保存连接
     * @return 带 id 的连接
     */
    Connection save(Connection connection);

    /**
     * 编辑连接（更新时间戳并持久化）。
     *
     * @param connection 待更新连接（须含 id）
     * @return 更新后的连接
     */
    Connection update(Connection connection);

    /**
     * 删除连接。
     *
     * @param id 主键
     */
    void delete(long id);

    /**
     * 按 id 查询。
     *
     * @param id 主键
     * @return 连接，不存在时为空
     */
    Optional<Connection> findById(long id);

    /**
     * 查询全部连接。
     *
     * @return 连接列表
     */
    List<Connection> findAll();

    /**
     * 按分组查询；{@code groupId} 为 null 时查询未分组连接。
     *
     * @param groupId 分组 id 或 null
     * @return 连接列表
     */
    List<Connection> findByGroup(Long groupId);
}
