-- for update from official xxljob

use `xxl_job`;

ALTER TABLE `xxl_job_group`
MODIFY COLUMN `title` VARCHAR(64) NOT NULL COMMENT 'Executor Name';


## —————————————————————— for default data ——————————————————

INSERT INTO `xxl_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`)
    VALUES (3, 'spring-executor', 'Spring Executor Name', 0, NULL, now());

UPDATE `xxl_job_group`
SET `title` = CASE WHEN `id` = 1 THEN 'Executor Sample'
                   WHEN `id` = 2 THEN 'AI Executor Sample'
              END
WHERE `id` IN (1, 2);

## —————————————————————— schedule_time feature ——————————————————

-- Add schedule_time column to xxl_job_log for theoretical schedule time tracking
-- This column stores the planned schedule time, null for manual/API triggers
ALTER TABLE `xxl_job_log`
ADD COLUMN `schedule_time` datetime DEFAULT NULL COMMENT 'Theoretical scheduling time; NULL when triggered manually.' AFTER `trigger_time`;