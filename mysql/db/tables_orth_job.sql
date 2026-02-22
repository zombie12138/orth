#
# Orth
# Copyright (c) 2015-present, xuxueli.

CREATE database if NOT EXISTS `orth_job` default character set utf8mb4 collate utf8mb4_unicode_ci;
use `orth_job`;

SET NAMES utf8mb4;

## —————————————————————— job group and registry ——————————————————

CREATE TABLE `orth_job_group`
(
    `id`           int(11)     NOT NULL AUTO_INCREMENT,
    `app_name`     varchar(64) NOT NULL COMMENT 'Executor app name',
    `title`        varchar(64) NOT NULL COMMENT 'Executor display name',
    `address_type` tinyint(4)  NOT NULL DEFAULT '0' COMMENT 'Address type: 0=auto-register, 1=manual',
    `address_list` text COMMENT 'Executor address list, comma-separated',
    `update_time`  datetime             DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `orth_job_registry`
(
    `id`             int(11)      NOT NULL AUTO_INCREMENT,
    `registry_group` varchar(50)  NOT NULL,
    `registry_key`   varchar(255) NOT NULL,
    `registry_value` varchar(255) NOT NULL,
    `update_time`    datetime DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_g_k_v` (`registry_group`, `registry_key`, `registry_value`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

## —————————————————————— job info ——————————————————

CREATE TABLE `orth_job_info`
(
    `id`                        int(11)      NOT NULL AUTO_INCREMENT,
    `job_group`                 int(11)      NOT NULL COMMENT 'Executor group ID',
    `job_desc`                  varchar(255) NOT NULL,
    `add_time`                  datetime              DEFAULT NULL,
    `update_time`               datetime              DEFAULT NULL,
    `author`                    varchar(64)           DEFAULT NULL COMMENT 'Author',
    `alarm_email`               varchar(255)          DEFAULT NULL COMMENT 'Alarm email addresses',
    `schedule_type`             varchar(50)  NOT NULL DEFAULT 'NONE' COMMENT 'Schedule type: CRON, FIX_RATE, NONE',
    `schedule_conf`             varchar(128)          DEFAULT NULL COMMENT 'Schedule config, meaning depends on schedule type',
    `misfire_strategy`          varchar(50)  NOT NULL DEFAULT 'DO_NOTHING' COMMENT 'Misfire strategy: DO_NOTHING, FIRE_ONCE_NOW',
    `executor_route_strategy`   varchar(50)           DEFAULT NULL COMMENT 'Executor routing strategy',
    `executor_handler`          varchar(255)          DEFAULT NULL COMMENT 'Job handler name',
    `executor_param`            varchar(512)          DEFAULT NULL COMMENT 'Job handler parameters',
    `executor_block_strategy`   varchar(50)           DEFAULT NULL COMMENT 'Block strategy when job is already running',
    `executor_timeout`          int(11)      NOT NULL DEFAULT '0' COMMENT 'Execution timeout in seconds, 0=unlimited',
    `executor_fail_retry_count` int(11)      NOT NULL DEFAULT '0' COMMENT 'Fail retry count',
    `glue_type`                 varchar(50)  NOT NULL COMMENT 'GLUE type: BEAN, GLUE_GROOVY, GLUE_SHELL, etc.',
    `glue_source`               mediumtext COMMENT 'GLUE source code',
    `glue_remark`               varchar(128)          DEFAULT NULL COMMENT 'GLUE remark',
    `glue_updatetime`           datetime              DEFAULT NULL COMMENT 'GLUE update time',
    `child_jobid`               varchar(255)          DEFAULT NULL COMMENT 'Child job IDs, comma-separated',
    `super_task_id`             int(11)      NOT NULL DEFAULT '0' COMMENT 'SuperTask ID, 0 means no parent',
    `trigger_status`            tinyint(4)   NOT NULL DEFAULT '0' COMMENT 'Trigger status: 0=stopped, 1=running',
    `trigger_last_time`         bigint(13)   NOT NULL DEFAULT '0' COMMENT 'Last trigger time (epoch ms)',
    `trigger_next_time`         bigint(13)   NOT NULL DEFAULT '0' COMMENT 'Next trigger time (epoch ms)',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `orth_job_logglue`
(
    `id`          int(11)      NOT NULL AUTO_INCREMENT,
    `job_id`      int(11)      NOT NULL COMMENT 'Job ID',
    `glue_type`   varchar(50) DEFAULT NULL COMMENT 'GLUE type',
    `glue_source` mediumtext COMMENT 'GLUE source code',
    `glue_remark` varchar(128) NOT NULL COMMENT 'GLUE remark',
    `add_time`    datetime    DEFAULT NULL,
    `update_time` datetime    DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

## —————————————————————— job log and report ——————————————————

CREATE TABLE `orth_job_log`
(
    `id`                        bigint(20) NOT NULL AUTO_INCREMENT,
    `job_group`                 int(11)    NOT NULL COMMENT 'Executor group ID',
    `job_id`                    int(11)    NOT NULL COMMENT 'Job ID',
    `executor_address`          varchar(255)        DEFAULT NULL COMMENT 'Executor address used for this execution',
    `executor_handler`          varchar(255)        DEFAULT NULL COMMENT 'Job handler name',
    `executor_param`            varchar(512)        DEFAULT NULL COMMENT 'Job handler parameters',
    `executor_sharding_param`   varchar(20)         DEFAULT NULL COMMENT 'Sharding parameters, e.g. 1/2',
    `executor_fail_retry_count` int(11)    NOT NULL DEFAULT '0' COMMENT 'Fail retry count',
    `trigger_time`              datetime            DEFAULT NULL COMMENT 'Trigger time',
    `schedule_time`             datetime            DEFAULT NULL COMMENT 'Theoretical schedule time; NULL when triggered manually',
    `trigger_code`              int(11)    NOT NULL COMMENT 'Trigger result code',
    `trigger_msg`               text COMMENT 'Trigger message/log',
    `handle_time`               datetime            DEFAULT NULL COMMENT 'Handle time',
    `handle_code`               int(11)    NOT NULL COMMENT 'Handle result code',
    `handle_msg`                text COMMENT 'Handle message/log',
    `alarm_status`              tinyint(4) NOT NULL DEFAULT '0' COMMENT 'Alarm status: 0=default, 1=skip, 2=sent, 3=failed',
    PRIMARY KEY (`id`),
    KEY `I_trigger_time` (`trigger_time`),
    KEY `I_handle_code` (`handle_code`),
    KEY `I_jobid_jobgroup` (`job_id`,`job_group`),
    KEY `I_job_id` (`job_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `orth_job_log_report`
(
    `id`            int(11) NOT NULL AUTO_INCREMENT,
    `trigger_day`   datetime         DEFAULT NULL COMMENT 'Report date',
    `running_count` int(11) NOT NULL DEFAULT '0' COMMENT 'Running log count',
    `suc_count`     int(11) NOT NULL DEFAULT '0' COMMENT 'Success log count',
    `fail_count`    int(11) NOT NULL DEFAULT '0' COMMENT 'Failure log count',
    `update_time`   datetime         DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_trigger_day` (`trigger_day`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

## —————————————————————— lock ——————————————————

CREATE TABLE `orth_job_lock`
(
    `lock_name` varchar(50) NOT NULL COMMENT 'Lock name',
    PRIMARY KEY (`lock_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

## —————————————————————— user ——————————————————

CREATE TABLE `orth_job_user`
(
    `id`         int(11)     NOT NULL AUTO_INCREMENT,
    `username`   varchar(50) NOT NULL COMMENT 'Username',
    `password`   varchar(100) NOT NULL COMMENT 'Hashed password',
    `token`      varchar(100) DEFAULT NULL COMMENT 'Login token',
    `role`       tinyint(4)  NOT NULL COMMENT 'Role: 0=normal, 1=admin',
    `permission` varchar(255) DEFAULT NULL COMMENT 'Permissions: executor group IDs, comma-separated',
    PRIMARY KEY (`id`),
    UNIQUE KEY `i_username` (`username`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


## —————————————————————— default seed data ——————————————————

INSERT INTO `orth_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
    VALUES (1, 'orth-executor-sample', 'Executor Sample', 0, NULL, now()),
           (2, 'orth-executor-sample-ai', 'AI Executor Sample', 0, NULL, now()),
           (3, 'spring-executor', 'Spring Executor', 0, NULL, now());

INSERT INTO `orth_job_info`(`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
                           `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
                           `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
                           `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`,
                           `child_jobid`)
VALUES (1, 1, 'Sample Job 01', now(), now(), 'admin', '', 'CRON', '0 0 0 * * ? *',
        'DO_NOTHING', 'FIRST', 'demoJobHandler', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'Initial GLUE code',
        now(), ''),
       (2, 2, 'Ollama Sample Job 01', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', 'ollamaJobHandler', '{
    "input": "Analyze slow SQL query patterns",
    "prompt": "You are a software engineer skilled at solving technical problems.",
    "model": "qwen3:0.6b"
}', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'Initial GLUE code',
        now(), ''),
       (3, 2, 'Dify Sample Job', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', 'difyWorkflowJobHandler', '{
    "inputs":{
        "input":"Query top 3 students per subject"
    },
    "user": "orth",
    "baseUrl": "http://localhost/v1",
    "apiKey": "app-OUVgNUOQRIMokfmuJvBJoUTN"
}', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'Initial GLUE code',
        now(), ''),
       (4, 1, 'Shell Script Template', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', '', '', 'SERIAL_EXECUTION', 0, 0, 'GLUE_SHELL',
        '#!/bin/bash\n# Orth Shell Script Template\n#\n# Environment variables (set by executor):\n#   ORTH_JOB_ID        - Job ID\n#   ORTH_JOB_PARAM     - Job parameters\n#   ORTH_LOG_ID        - Log ID for tracking\n#   ORTH_SCHEDULE_TIME - Scheduled time (ISO 8601, empty if manual)\n#   ORTH_TRIGGER_TIME  - Actual trigger time (ISO 8601)\n#   ORTH_SHARD_INDEX   - Shard index (0-based)\n#   ORTH_SHARD_TOTAL   - Total shard count\n#\n# Positional args: $1=jobParam $2=shardIndex $3=shardTotal\n\necho \"[Orth] Job=$ORTH_JOB_ID Param=$ORTH_JOB_PARAM\"\necho \"[Orth] Schedule=$ORTH_SCHEDULE_TIME Trigger=$ORTH_TRIGGER_TIME\"\necho \"[Orth] Shard=$ORTH_SHARD_INDEX/$ORTH_SHARD_TOTAL\"\n\n# --- Your logic below ---\n\nexit 0',
        'Shell script template with env vars', now(), ''),
       (5, 1, 'Python Script Template', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', '', '', 'SERIAL_EXECUTION', 0, 0, 'GLUE_PYTHON',
        '#!/usr/bin/env python3\n# Orth Python Script Template\n#\n# Environment variables (set by executor):\n#   ORTH_JOB_ID        - Job ID\n#   ORTH_JOB_PARAM     - Job parameters\n#   ORTH_LOG_ID        - Log ID for tracking\n#   ORTH_SCHEDULE_TIME - Scheduled time (ISO 8601, empty if manual)\n#   ORTH_TRIGGER_TIME  - Actual trigger time (ISO 8601)\n#   ORTH_SHARD_INDEX   - Shard index (0-based)\n#   ORTH_SHARD_TOTAL   - Total shard count\n#\n# Positional args: sys.argv[1]=jobParam sys.argv[2]=shardIndex sys.argv[3]=shardTotal\n\nimport os, sys\n\njob_id = os.environ.get(\"ORTH_JOB_ID\", \"\")\njob_param = os.environ.get(\"ORTH_JOB_PARAM\", \"\")\nschedule_time = os.environ.get(\"ORTH_SCHEDULE_TIME\", \"\")\ntrigger_time = os.environ.get(\"ORTH_TRIGGER_TIME\", \"\")\nshard_index = os.environ.get(\"ORTH_SHARD_INDEX\", \"0\")\nshard_total = os.environ.get(\"ORTH_SHARD_TOTAL\", \"1\")\n\nprint(f\"[Orth] Job={job_id} Param={job_param}\")\nprint(f\"[Orth] Schedule={schedule_time} Trigger={trigger_time}\")\nprint(f\"[Orth] Shard={shard_index}/{shard_total}\")\n\n# --- Your logic below ---\n\nsys.exit(0)',
        'Python script template with env vars', now(), '');

INSERT INTO `orth_job_user`(`id`, `username`, `password`, `role`, `permission`)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 1, NULL);

INSERT INTO `orth_job_lock` (`lock_name`)
VALUES ('schedule_lock');

commit;
