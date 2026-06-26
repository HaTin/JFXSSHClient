package com.xxx.jfxssh.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.entity.Group;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 连接 / 分组的导入导出（JSON）。
 *
 * <p><b>不导出密码</b>：连接密码以密文存储、且密钥由本机主密码派生，导出到其它机器
 * 无法解密；导出明文则不安全。因此导出仅含连接元数据与分组结构，密码留空。</p>
 *
 * <p>导入为幂等合并：分组按"名称 + 父分组"去重复用，连接按"名称|主机|端口|用户名"
 * 去重；不合法的连接条目跳过，不影响整体导入。</p>
 */
public final class ConnectionPortService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPortService.class);
    private static final int FORMAT_VERSION = 1;

    private final ConnectionService connectionService;
    private final GroupService groupService;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * @param connectionService 连接服务
     * @param groupService      分组服务
     */
    public ConnectionPortService(ConnectionService connectionService, GroupService groupService) {
        this.connectionService = connectionService;
        this.groupService = groupService;
    }

    /** 导出数据根（不含密码）。 */
    public record ExportData(int version, List<GroupDto> groups, List<ConnectionDto> connections) {
    }

    /** 分组导出条目。 */
    public record GroupDto(Long id, String name, Long parentId, int sort) {
    }

    /** 连接导出条目（无 password_enc）。 */
    public record ConnectionDto(String name, String host, int port, String username, String authType,
                                String privateKeyPath, Long groupId, String remark, String terminalType) {
    }

    /**
     * 导出到文件。
     *
     * @param file 目标文件
     * @return [连接数, 分组数]
     */
    public int[] exportTo(Path file) {
        List<GroupDto> groups = groupService.findAll().stream()
                .map(g -> new GroupDto(g.getId(), g.getName(), g.getParentId(), g.getSort()))
                .toList();
        List<ConnectionDto> connections = connectionService.findAll().stream()
                .map(c -> new ConnectionDto(c.getName(), c.getHost(), c.getPort(), c.getUsername(),
                        c.getAuthType() == null ? null : c.getAuthType().name(),
                        c.getPrivateKeyPath(), c.getGroupId(), c.getRemark(), c.getTerminalType()))
                .toList();
        try {
            mapper.writeValue(file.toFile(), new ExportData(FORMAT_VERSION, groups, connections));
        } catch (IOException e) {
            throw new ConnectionPortException("Export failed: " + e.getMessage(), e);
        }
        return new int[]{connections.size(), groups.size()};
    }

    /**
     * 从文件导入（合并去重）。
     *
     * @param file 源文件
     * @return [导入连接数, 跳过连接数]
     */
    public int[] importFrom(Path file) {
        ExportData data;
        try {
            data = mapper.readValue(file.toFile(), ExportData.class);
        } catch (IOException e) {
            throw new ConnectionPortException("Import failed: " + e.getMessage(), e);
        }
        Map<Long, Long> idMap = importGroups(data.groups());
        return importConnections(data.connections(), idMap);
    }

    private Map<Long, Long> importGroups(List<GroupDto> groups) {
        Map<Long, Long> idMap = new HashMap<>();
        if (groups == null || groups.isEmpty()) {
            return idMap;
        }
        List<Group> existing = new ArrayList<>(groupService.findAll());
        List<GroupDto> pending = new ArrayList<>(groups);
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (Iterator<GroupDto> it = pending.iterator(); it.hasNext(); ) {
                GroupDto g = it.next();
                Long oldParent = g.parentId();
                if (oldParent != null && !idMap.containsKey(oldParent)) {
                    continue; // 父分组尚未解析
                }
                Long newParent = oldParent == null ? null : idMap.get(oldParent);
                Long newId = resolveOrCreateGroup(existing, g, newParent);
                if (g.id() != null) {
                    idMap.put(g.id(), newId);
                }
                it.remove();
                progress = true;
            }
        }
        // 剩余（父缺失/环）：作为根导入
        for (GroupDto g : pending) {
            Long newId = resolveOrCreateGroup(existing, g, null);
            if (g.id() != null) {
                idMap.put(g.id(), newId);
            }
        }
        return idMap;
    }

    private Long resolveOrCreateGroup(List<Group> existing, GroupDto g, Long newParent) {
        for (Group e : existing) {
            if (Objects.equals(e.getName(), g.name()) && Objects.equals(e.getParentId(), newParent)) {
                return e.getId();
            }
        }
        Group ng = new Group();
        ng.setName(g.name());
        ng.setParentId(newParent);
        ng.setSort(g.sort());
        Group saved = groupService.save(ng);
        existing.add(saved);
        return saved.getId();
    }

    private int[] importConnections(List<ConnectionDto> conns, Map<Long, Long> idMap) {
        if (conns == null || conns.isEmpty()) {
            return new int[]{0, 0};
        }
        Set<String> seen = new HashSet<>();
        for (Connection c : connectionService.findAll()) {
            seen.add(connKey(c.getName(), c.getHost(), c.getPort(), c.getUsername()));
        }
        int imported = 0;
        int skipped = 0;
        for (ConnectionDto d : conns) {
            String key = connKey(d.name(), d.host(), d.port(), d.username());
            if (seen.contains(key)) {
                skipped++;
                continue;
            }
            try {
                Connection c = new Connection();
                c.setName(d.name());
                c.setHost(d.host());
                c.setPort(d.port());
                c.setUsername(d.username());
                c.setAuthType(parseAuth(d.authType()));
                c.setPrivateKeyPath(d.privateKeyPath());
                c.setGroupId(d.groupId() == null ? null : idMap.get(d.groupId()));
                c.setRemark(d.remark());
                c.setTerminalType(d.terminalType());
                connectionService.save(c);
                seen.add(key);
                imported++;
            } catch (RuntimeException e) {
                log.warn("Skip invalid imported connection '{}': {}", d.name(), e.getMessage());
                skipped++;
            }
        }
        return new int[]{imported, skipped};
    }

    private AuthType parseAuth(String value) {
        if (value == null) {
            return AuthType.PASSWORD;
        }
        try {
            return AuthType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return AuthType.PASSWORD;
        }
    }

    private String connKey(String name, String host, int port, String username) {
        return name + "|" + host + "|" + port + "|" + username;
    }
}
