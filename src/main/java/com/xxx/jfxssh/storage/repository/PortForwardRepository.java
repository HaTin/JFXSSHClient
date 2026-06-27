package com.xxx.jfxssh.storage.repository;

import com.xxx.jfxssh.storage.entity.PortForwardRule;

import java.util.List;
import java.util.Optional;

/**
 * 端口转发规则数据访问接口。
 */
public interface PortForwardRepository {

    /**
     * 插入规则并回填生成的主键。
     *
     * @param rule 规则（id 可为 null）
     * @return 插入后的规则（含 id）
     */
    PortForwardRule insert(PortForwardRule rule);

    /**
     * 更新规则。
     *
     * @param rule 规则（id 必须非空）
     */
    void update(PortForwardRule rule);

    /**
     * 删除规则。
     *
     * @param id 规则 id
     */
    void delete(long id);

    /**
     * 按 id 查询规则。
     *
     * @param id 规则 id
     * @return 规则（可能为空）
     */
    Optional<PortForwardRule> findById(long id);

    /**
     * 查询某连接下的所有规则。
     *
     * @param connectionId 连接 id
     * @return 规则列表（按名称排序）
     */
    List<PortForwardRule> findByConnection(long connectionId);
}
