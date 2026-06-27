package com.xxx.jfxssh.service;

import com.xxx.jfxssh.ssh.PortForwardSpec;
import com.xxx.jfxssh.storage.entity.PortForwardRule;
import com.xxx.jfxssh.storage.repository.PortForwardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@link PortForwardService} 的默认实现。
 *
 * <p>负责入参校验、时间戳维护，并委派 {@link PortForwardRepository} 持久化。
 * 依赖通过构造器注入。</p>
 */
public final class PortForwardServiceImpl implements PortForwardService {

    private static final Logger log = LoggerFactory.getLogger(PortForwardServiceImpl.class);

    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 65535;

    private final PortForwardRepository repository;

    /**
     * @param repository 端口转发规则仓库（构造器注入）
     */
    public PortForwardServiceImpl(PortForwardRepository repository) {
        this.repository = repository;
    }

    @Override
    public PortForwardRule save(PortForwardRule rule) {
        validate(rule);
        String now = OffsetDateTime.now().toString();
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        PortForwardRule saved = repository.insert(rule);
        log.info("Port forward rule saved: id={}, connectionId={}, name={}",
                saved.getId(), saved.getConnectionId(), saved.getName());
        return saved;
    }

    @Override
    public PortForwardRule update(PortForwardRule rule) {
        if (rule.getId() == null) {
            throw new IllegalArgumentException("Port forward rule id is required for update");
        }
        validate(rule);
        rule.setUpdateTime(OffsetDateTime.now().toString());
        repository.update(rule);
        log.info("Port forward rule updated: id={}, connectionId={}, name={}",
                rule.getId(), rule.getConnectionId(), rule.getName());
        return rule;
    }

    @Override
    public void delete(long id) {
        repository.delete(id);
        log.info("Port forward rule deleted: id={}", id);
    }

    @Override
    public List<PortForwardRule> findByConnection(long connectionId) {
        return repository.findByConnection(connectionId);
    }

    private void validate(PortForwardRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Port forward rule must not be null");
        }
        if (rule.getConnectionId() == null) {
            throw new IllegalArgumentException("Connection id must not be null");
        }
        if (isBlank(rule.getName())) {
            throw new IllegalArgumentException("Rule name must not be blank");
        }
        if (rule.getType() == null) {
            throw new IllegalArgumentException("Rule type must not be null");
        }
        if (isBlank(rule.getBindHost())) {
            throw new IllegalArgumentException("Bind host must not be blank");
        }
        if (rule.getBindPort() < MIN_PORT || rule.getBindPort() > MAX_PORT) {
            throw new IllegalArgumentException("Bind port out of range: " + rule.getBindPort());
        }
        if (rule.getType() != PortForwardSpec.Type.DYNAMIC) {
            if (isBlank(rule.getDestHost())) {
                throw new IllegalArgumentException("Destination host is required for " + rule.getType());
            }
            if (rule.getDestPort() < 1 || rule.getDestPort() > MAX_PORT) {
                throw new IllegalArgumentException("Destination port out of range: " + rule.getDestPort());
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
