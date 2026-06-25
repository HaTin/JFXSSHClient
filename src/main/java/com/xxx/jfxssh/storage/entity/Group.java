package com.xxx.jfxssh.storage.entity;

/**
 * 分组实体（对应 docs/DATABASE.md 的 groups 表）。
 *
 * <p>纯数据载体，支持树形嵌套：{@code parentId} 为 null 表示根分组。</p>
 */
public final class Group {

    private Long id;
    private String name;
    private Long parentId;
    private int sort;
    private String createTime;
    private String updateTime;

    /** @return 主键，未持久化时为 null */
    public Long getId() {
        return id;
    }

    /** @param id 主键 */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return 分组名称 */
    public String getName() {
        return name;
    }

    /** @param name 分组名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** @return 父分组 id（根分组为 null） */
    public Long getParentId() {
        return parentId;
    }

    /** @param parentId 父分组 id */
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    /** @return 同级排序 */
    public int getSort() {
        return sort;
    }

    /** @param sort 同级排序 */
    public void setSort(int sort) {
        this.sort = sort;
    }

    /** @return 创建时间（ISO-8601） */
    public String getCreateTime() {
        return createTime;
    }

    /** @param createTime 创建时间（ISO-8601） */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    /** @return 更新时间（ISO-8601） */
    public String getUpdateTime() {
        return updateTime;
    }

    /** @param updateTime 更新时间（ISO-8601） */
    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
