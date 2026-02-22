-- for update from official xxl-job

use `orth_job`;

ALTER TABLE `orth_job_group`
MODIFY COLUMN `title` VARCHAR(64) NOT NULL COMMENT 'Executor Name';


## —————————————————————— for default data ——————————————————

INSERT INTO `orth_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
    VALUES (3, 'spring-executor', 'Spring Executor Name', 0, NULL, now());

UPDATE `orth_job_group`
SET `title` = CASE WHEN `id` = 1 THEN 'Executor Sample'
                   WHEN `id` = 2 THEN 'AI Executor Sample'
              END
WHERE `id` IN (1, 2);

## —————————————————————— schedule_time feature ——————————————————

-- Add schedule_time column to orth_job_log for theoretical schedule time tracking
-- This column stores the planned schedule time, null for manual/API triggers
ALTER TABLE `orth_job_log`
ADD COLUMN `schedule_time` datetime DEFAULT NULL COMMENT 'Theoretical scheduling time; NULL when triggered manually.' AFTER `trigger_time`;

## —————————————————————— SuperTask feature ——————————————————

-- Add super_task_id column to orth_job_info for template-instance pattern
-- This enables batch creation of similar jobs that share code but differ in parameters
ALTER TABLE `orth_job_info`
ADD COLUMN `super_task_id` int(11) DEFAULT NULL COMMENT 'SuperTask ID (NULL = standalone or template task)' AFTER `child_jobid`,
ADD INDEX `idx_super_task_id` (`super_task_id`);

## —————————————————————— Rename xxl_job → orth_job ——————————————————

-- Step 1: Rename tables
RENAME TABLE
    `xxl_job_group` TO `orth_job_group`,
    `xxl_job_registry` TO `orth_job_registry`,
    `xxl_job_info` TO `orth_job_info`,
    `xxl_job_logglue` TO `orth_job_logglue`,
    `xxl_job_log` TO `orth_job_log`,
    `xxl_job_log_report` TO `orth_job_log_report`,
    `xxl_job_lock` TO `orth_job_lock`,
    `xxl_job_user` TO `orth_job_user`;

-- Step 2: Update seed data references
UPDATE `orth_job_group` SET `app_name` = REPLACE(`app_name`, 'xxl-job-', 'orth-') WHERE `app_name` LIKE 'xxl-job-%';
UPDATE `orth_job_info` SET `author` = 'Orth' WHERE `author` = 'XXL';