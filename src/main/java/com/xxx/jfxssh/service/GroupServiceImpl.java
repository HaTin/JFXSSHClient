package com.xxx.jfxssh.service;

import com.xxx.jfxssh.storage.entity.Group;
import com.xxx.jfxssh.storage.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link GroupService} 的默认实现。
 *
 * <p>负责入参校验、时间戳维护、树构建，并委派 {@link GroupRepository} 持久化。
 * 依赖通过构造器注入。</p>
 */
public final class GroupServiceImpl implements GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupServiceImpl.class);

    private final GroupRepository repository;

    /**
     * @param repository 分组仓库（构造器注入）
     */
    public GroupServiceImpl(GroupRepository repository) {
        this.repository = repository;
    }

    @Override
    public Group save(Group group) {
        if (group == null) {
            throw new IllegalArgumentException("Group must not be null");
        }
        requireName(group.getName());
        String now = OffsetDateTime.now().toString();
        group.setCreateTime(now);
        group.setUpdateTime(now);
        Group saved = repository.insert(group);
        log.info("Group saved: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Override
    public Group rename(long id, String name) {
        requireName(name);
        Group group = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
        group.setName(name);
        group.setUpdateTime(OffsetDateTime.now().toString());
        repository.update(group);
        log.info("Group renamed: id={}, name={}", id, name);
        return group;
    }

    @Override
    public void delete(long id) {
        repository.delete(id);
        log.info("Group deleted: id={}", id);
    }

    @Override
    public Optional<Group> findById(long id) {
        return repository.findById(id);
    }

    @Override
    public List<Group> findAll() {
        return repository.findAll();
    }

    @Override
    public List<GroupNode> findTree() {
        List<Group> all = repository.findAll();

        Map<Long, GroupNode> nodes = new HashMap<>();
        for (Group g : all) {
            nodes.put(g.getId(), new GroupNode(g));
        }

        List<GroupNode> roots = new ArrayList<>();
        for (Group g : all) {
            GroupNode node = nodes.get(g.getId());
            GroupNode parent = g.getParentId() == null ? null : nodes.get(g.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Group name must not be blank");
        }
    }
}
