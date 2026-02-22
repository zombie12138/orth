package com.abyss.orth.admin.model;

import java.util.Date;

import lombok.Data;

/**
 * Executor registry entity for service discovery.
 *
 * <p>Stores executor heartbeat information for automatic service registration and health
 * monitoring.
 */
@Data
public class JobRegistry {

    private int id;
    private String registryGroup;
    private String registryKey;
    private String registryValue;
    private Date updateTime;
}
