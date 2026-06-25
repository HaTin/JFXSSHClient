package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.storage.entity.Connection;

import java.util.List;
import java.util.Optional;

/**
 * 连接数据访问接口（Repository 层，见 docs/ARCHITECTURE.md）。
 *
 * <p>只负责数据库访问，不含业务逻辑。实现类对上仅暴露本接口。</p>
 */
public interface ConnectionRepository {

    /**
     * 插入新连接，回填生成的主键。
     *
     * @param connection 待插入连接
     * @return 带 id 的连接
     */
    Connection insert(Connection connection);

    /**
     * 更新已有连接。
     *
     * @param connection 待更新连接（须含 id）
     */
    void update(Connection connection);

    /**
     * 按 id 删除连接。
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
