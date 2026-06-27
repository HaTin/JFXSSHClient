package com.xxx.jfxssh.service;

import com.xxx.jfxssh.storage.entity.PortForwardRule;

import java.util.List;

/**
 * 端口转发规则业务服务。
 */
public interface PortForwardService {

    /**
     * 保存规则（新增）。
     *
     * @param rule 规则（id 可为 null）
     * @return 保存后的规则（含 id）
     */
    PortForwardRule save(PortForwardRule rule);

    /**
     * 更新规则。
     *
     * @param rule 规则（id 必须非空）
     * @return 更新后的规则
     */
    PortForwardRule update(PortForwardRule rule);

    /**
     * 删除规则。
     *
     * @param id 规则 id
     */
    void delete(long id);

    /**
     * 查询某连接下的所有规则。
     *
     * @param connectionId 连接 id
     * @return 规则列表（按名称排序）
     */
    List<PortForwardRule> findByConnection(long connectionId);

    /**
     * 查询某连接下所有自动启动的规则。
     *
     * @param connectionId 连接 id
     * @return 自动启动的规则列表（按名称排序）
     */
    List<PortForwardRule> findAutoStartByConnection(long connectionId);

}
