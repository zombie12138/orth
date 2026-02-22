package com.abyss.orth.admin.model.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resource tree node DTO for RBAC (Role-Based Access Control).
 *
 * <p>Represents hierarchical system resources including directories, menus, and actions.
 */
@Getter
@Setter
@NoArgsConstructor
public class OrthBootResourceDTO implements Serializable {
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
    private List<OrthBootResourceDTO> children;

    public OrthBootResourceDTO(
            int id,
            int parentId,
            String name,
            int type,
            String permission,
            String url,
            String icon,
            int order,
            int status,
            List<OrthBootResourceDTO> children) {
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
}
