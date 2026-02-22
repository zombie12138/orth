package com.abyss.orth.core.constant;

/**
 * Registry type enumeration for service discovery.
 *
 * <p>Defines the type of service being registered in the distributed scheduling system.
 */
public enum RegistType {

    /** Executor node registration (worker instances that execute tasks) */
    EXECUTOR,

    /** Admin node registration (scheduler instances that manage tasks) */
    ADMIN
}
