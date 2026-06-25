package com.xxx.jfxssh.service;

import com.xxx.jfxssh.common.AuthType;
import com.xxx.jfxssh.storage.entity.Connection;
import com.xxx.jfxssh.storage.repository.ConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link ConnectionService} 的默认实现。
 *
 * <p>负责入参校验、时间戳维护，并委派 {@link ConnectionRepository} 持久化。
 * 依赖通过构造器注入（见 docs/CODING_STANDARDS.md）。</p>
 *
 * <p>注：凭据加密（password_enc）由加密模块负责（见 ARCHITECTURE.md 加密方案，
 * 任务7）。本服务将 passwordEnc 视为不透明密文，不在此处加解密。</p>
 */
public final class ConnectionServiceImpl implements ConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionServiceImpl.class);

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final ConnectionRepository repository;

    /**
     * @param repository 连接仓库（构造器注入）
     */
    public ConnectionServiceImpl(ConnectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Connection save(Connection connection) {
        validate(connection);
        String now = OffsetDateTime.now().toString();
        connection.setCreateTime(now);
        connection.setUpdateTime(now);
        Connection saved = repository.insert(connection);
        log.info("Connection saved: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    public Connection update(Connection connection) {
        if (connection.getId() == null) {
            throw new IllegalArgumentException("Connection id is required for update");
        }
        validate(connection);
        connection.setUpdateTime(OffsetDateTime.now().toString());
        repository.update(connection);
        log.info("Connection updated: id={}, name={}", connection.getId(), connection.getName());
        return connection;
    }

    @Override
    public void delete(long id) {
        repository.delete(id);
        log.info("Connection deleted: id={}", id);
    }

    @Override
    public Optional<Connection> findById(long id) {
        return repository.findById(id);
    }

    @Override
    public List<Connection> findAll() {
        return repository.findAll();
    }

    @Override
    public List<Connection> findByGroup(Long groupId) {
        return repository.findByGroup(groupId);
    }

    private void validate(Connection c) {
        if (c == null) {
            throw new IllegalArgumentException("Connection must not be null");
        }
        if (isBlank(c.getName())) {
            throw new IllegalArgumentException("Connection name must not be blank");
        }
        if (isBlank(c.getHost())) {
            throw new IllegalArgumentException("Connection host must not be blank");
        }
        if (c.getPort() < MIN_PORT || c.getPort() > MAX_PORT) {
            throw new IllegalArgumentException("Port out of range: " + c.getPort());
        }
        if (c.getAuthType() == null) {
            throw new IllegalArgumentException("Auth type must not be null");
        }
        if (c.getAuthType() == AuthType.PRIVATE_KEY && isBlank(c.getPrivateKeyPath())) {
            throw new IllegalArgumentException("Private key path required for PRIVATE_KEY auth");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
