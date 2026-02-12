package com.xxl.job.admin.model.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Resource tree node DTO for RBAC (Role-Based Access Control).
 *
 * <p>Represents hierarchical system resources including directories, menus, and actions.
 */
public class XxlBootResourceDTO implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Resource type: Directory */
    public static final int TYPE_DIRECTORY = 0;

    /** Resource type: Menu */
    public static final int TYPE_MENU = 1;

    /** Resource type: Button/Action */
    public static final int TYPE_BUTTON = 2;

    /** Resource status: Active */
    public static final int STATUS_ACTIVE = 0;

    /** Resource status: Disabled */
    public static final int STATUS_DISABLED = 1;

    /** Resource ID */
    private int id;

    /** Parent node ID (0 for root nodes) */
    private int parentId;

    /** Display name */
    private String name;

    /** Resource type (TYPE_DIRECTORY, TYPE_MENU, or TYPE_BUTTON) */
    private int type;

    /** Permission identifier (e.g., "job:create") */
    private String permission;

    /** Menu URL path */
    private String url;

    /** Icon class name */
    private String icon;

    /** Display order for sorting */
    private int order;

    /** Resource status (STATUS_ACTIVE or STATUS_DISABLED) */
    private int status;

    /** Creation timestamp */
    private Date addTime;

    /** Last update timestamp */
    private Date updateTime;

    /** Child resources (hierarchical tree structure) */
    private List<XxlBootResourceDTO> children;

    public XxlBootResourceDTO() {}

    public XxlBootResourceDTO(
            int id,
            int parentId,
            String name,
            int type,
            String permission,
            String url,
            String icon,
            int order,
            int status,
            List<XxlBootResourceDTO> children) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.type = type;
        this.permission = permission;
        this.url = url;
        this.icon = icon;
        this.order = order;
        this.status = status;
        this.children = children;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getAddTime() {
        return addTime;
    }

    public void setAddTime(Date addTime) {
        this.addTime = addTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public List<XxlBootResourceDTO> getChildren() {
        return children;
    }

    public void setChildren(List<XxlBootResourceDTO> children) {
        this.children = children;
    }
}
