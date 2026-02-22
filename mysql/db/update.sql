-- ================================================================
-- Migration: vanilla xxl-job -> Orth
-- ================================================================
-- Apply this to a standard xxl-job installation to migrate to Orth.
--
-- Prerequisites:
--   - Running xxl-job with database 'xxl_job'
--   - All xxl_job_* tables exist
--
-- This script will:
--   1. Apply Orth schema changes (new columns, modified columns)
--   2. Create 'orth_job' database and migrate all tables
--   3. Update seed data for Orth branding
--   4. Insert Orth-specific seed data (executor groups, GLUE templates)
-- ================================================================


-- ═══════════ Step 1: Schema changes on existing xxl_job tables ═══════════

USE `xxl_job`;

-- Widen title column (xxl-job original is varchar(12))
ALTER TABLE `xxl_job_group`
    MODIFY COLUMN `title` VARCHAR(64) NOT NULL COMMENT 'Executor display name';

-- Add schedule_time: theoretical schedule time tracking
-- NULL for manual/API triggers, set for scheduled triggers
ALTER TABLE `xxl_job_log`
    ADD COLUMN `schedule_time` datetime DEFAULT NULL
        COMMENT 'Theoretical schedule time; NULL when triggered manually'
        AFTER `trigger_time`;

-- Add super_task_id: template-instance (SuperTask) pattern
-- 0 = standalone job, >0 = cloned from that SuperTask template
ALTER TABLE `xxl_job_info`
    ADD COLUMN `super_task_id` int(11) NOT NULL DEFAULT '0'
        COMMENT 'SuperTask ID, 0 means no parent'
        AFTER `child_jobid`,
    ADD INDEX `idx_super_task_id` (`super_task_id`);


-- ═══════════ Step 2: Create orth_job database and migrate tables ═══════════

CREATE DATABASE IF NOT EXISTS `orth_job`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

RENAME TABLE
    `xxl_job`.`xxl_job_group`      TO `orth_job`.`orth_job_group`,
    `xxl_job`.`xxl_job_registry`   TO `orth_job`.`orth_job_registry`,
    `xxl_job`.`xxl_job_info`       TO `orth_job`.`orth_job_info`,
    `xxl_job`.`xxl_job_logglue`    TO `orth_job`.`orth_job_logglue`,
    `xxl_job`.`xxl_job_log`        TO `orth_job`.`orth_job_log`,
    `xxl_job`.`xxl_job_log_report` TO `orth_job`.`orth_job_log_report`,
    `xxl_job`.`xxl_job_lock`       TO `orth_job`.`orth_job_lock`,
    `xxl_job`.`xxl_job_user`       TO `orth_job`.`orth_job_user`;

DROP DATABASE IF EXISTS `xxl_job`;


-- ═══════════ Step 3: Update existing seed data ═══════════

USE `orth_job`;

-- Rebrand executor group app names
UPDATE `orth_job_group`
SET `app_name` = REPLACE(`app_name`, 'xxl-job-', 'orth-')
WHERE `app_name` LIKE 'xxl-job-%';

-- Rebrand job author (XXL default was 'XXL', change to 'admin')
UPDATE `orth_job_info` SET `author` = 'admin' WHERE `author` = 'XXL';


-- ═══════════ Step 4: Insert Orth-specific seed data ═══════════

-- AI Executor group
INSERT IGNORE INTO `orth_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
VALUES (2, 'orth-executor-sample-ai', 'AI Executor Sample', 0, NULL, now());

-- Spring Executor group
INSERT IGNORE INTO `orth_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
VALUES (3, 'spring-executor', 'Spring Executor', 0, NULL, now());

-- Ollama AI sample job
INSERT IGNORE INTO `orth_job_info`(`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
                                   `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
                                   `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
                                   `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
                                   `glue_updatetime`, `child_jobid`)
VALUES (2, 2, 'Ollama Sample Job 01', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', 'ollamaJobHandler', '{
    "input": "Analyze slow SQL query patterns",
    "prompt": "You are a software engineer skilled at solving technical problems.",
    "model": "qwen3:0.6b"
}', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'Initial GLUE code', now(), '');

-- Dify workflow sample job
INSERT IGNORE INTO `orth_job_info`(`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
                                   `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
                                   `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
                                   `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
                                   `glue_updatetime`, `child_jobid`)
VALUES (3, 2, 'Dify Sample Job', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', 'difyWorkflowJobHandler', '{
    "inputs":{
        "input":"Query top 3 students per subject"
    },
    "user": "orth",
    "baseUrl": "http://localhost/v1",
    "apiKey": "app-OUVgNUOQRIMokfmuJvBJoUTN"
}', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', 'Initial GLUE code', now(), '');

-- Shell script template job
INSERT IGNORE INTO `orth_job_info`(`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
                                   `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
                                   `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
                                   `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
                                   `glue_updatetime`, `child_jobid`)
VALUES (4, 1, 'Shell Script Template', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', '', '', 'SERIAL_EXECUTION', 0, 0, 'GLUE_SHELL',
        '#!/bin/bash\n# Orth Shell Script Template\n#\n# Environment variables (set by executor):\n#   ORTH_JOB_ID        - Job ID\n#   ORTH_JOB_PARAM     - Job parameters\n#   ORTH_LOG_ID        - Log ID for tracking\n#   ORTH_SCHEDULE_TIME - Scheduled time (ISO 8601, empty if manual)\n#   ORTH_TRIGGER_TIME  - Actual trigger time (ISO 8601)\n#   ORTH_SHARD_INDEX   - Shard index (0-based)\n#   ORTH_SHARD_TOTAL   - Total shard count\n#\n# Positional args: $1=jobParam $2=shardIndex $3=shardTotal\n\necho \"[Orth] Job=$ORTH_JOB_ID Param=$ORTH_JOB_PARAM\"\necho \"[Orth] Schedule=$ORTH_SCHEDULE_TIME Trigger=$ORTH_TRIGGER_TIME\"\necho \"[Orth] Shard=$ORTH_SHARD_INDEX/$ORTH_SHARD_TOTAL\"\n\n# --- Your logic below ---\n\nexit 0',
        'Shell script template with env vars', now(), '');

-- Python script template job
INSERT IGNORE INTO `orth_job_info`(`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`,
                                   `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`,
                                   `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
                                   `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`,
                                   `glue_updatetime`, `child_jobid`)
VALUES (5, 1, 'Python Script Template', now(), now(), 'admin', '', 'NONE', '',
        'DO_NOTHING', 'FIRST', '', '', 'SERIAL_EXECUTION', 0, 0, 'GLUE_PYTHON',
        '#!/usr/bin/env python3\n# Orth Python Script Template\n#\n# Environment variables (set by executor):\n#   ORTH_JOB_ID        - Job ID\n#   ORTH_JOB_PARAM     - Job parameters\n#   ORTH_LOG_ID        - Log ID for tracking\n#   ORTH_SCHEDULE_TIME - Scheduled time (ISO 8601, empty if manual)\n#   ORTH_TRIGGER_TIME  - Actual trigger time (ISO 8601)\n#   ORTH_SHARD_INDEX   - Shard index (0-based)\n#   ORTH_SHARD_TOTAL   - Total shard count\n#\n# Positional args: sys.argv[1]=jobParam sys.argv[2]=shardIndex sys.argv[3]=shardTotal\n\nimport os, sys\n\njob_id = os.environ.get(\"ORTH_JOB_ID\", \"\")\njob_param = os.environ.get(\"ORTH_JOB_PARAM\", \"\")\nschedule_time = os.environ.get(\"ORTH_SCHEDULE_TIME\", \"\")\ntrigger_time = os.environ.get(\"ORTH_TRIGGER_TIME\", \"\")\nshard_index = os.environ.get(\"ORTH_SHARD_INDEX\", \"0\")\nshard_total = os.environ.get(\"ORTH_SHARD_TOTAL\", \"1\")\n\nprint(f\"[Orth] Job={job_id} Param={job_param}\")\nprint(f\"[Orth] Schedule={schedule_time} Trigger={trigger_time}\")\nprint(f\"[Orth] Shard={shard_index}/{shard_total}\")\n\n# --- Your logic below ---\n\nsys.exit(0)',
        'Python script template with env vars', now(), '');

COMMIT;
