package com.abyss.orth.core.openapi.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Executor registry request for service discovery.
 *
 * <p>Executors send this request to admin every 30 seconds to register/heartbeat their presence.
 * The admin maintains a registry table ({@code orth_job_registry}) that tracks active executors and
 * builds executor address lists for routing.
 *
 * <p>Registry lifecycle:
 *
 * <ol>
 *   <li>Executor starts and sends initial registry request
 *   <li>Executor sends heartbeat every 30 seconds with same data
 *   <li>Admin updates {@code update_time} on each heartbeat
 *   <li>Admin removes entries idle for 90+ seconds (3 missed heartbeats)
 * </ol>
 *
 * <p>Registry key structure:
 *
 * <ul>
 *   <li>{@link #registryGroup} - "EXECUTOR" (executor type identifier)
 *   <li>{@link #registryKey} - Application name (e.g., "sample-executor")
 *   <li>{@link #registryValue} - Executor address (e.g., "http://127.0.0.1:9999")
 * </ul>
 *
 * @author xuxueli 2017-05-10 20:22:42
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistryRequest implements Serializable {
    private static final long serialVersionUID = 42L;

    /** Registry group (always "EXECUTOR" for executor registration) */
    private String registryGroup;

    /** Registry key (executor application name) */
    private String registryKey;

    /** Registry value (executor address URL) */
    private String registryValue;

    @Override
    public String toString() {
        return "RegistryRequest{"
                + "registryGroup='"
                + registryGroup
                + '\''
                + ", registryKey='"
                + registryKey
                + '\''
                + ", registryValue='"
                + registryValue
                + '\''
                + '}';
    }
}
