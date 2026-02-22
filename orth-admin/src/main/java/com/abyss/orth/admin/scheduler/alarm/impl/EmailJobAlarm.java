package com.abyss.orth.admin.scheduler.alarm.impl;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.abyss.orth.admin.model.JobGroup;
import com.abyss.orth.admin.model.JobInfo;
import com.abyss.orth.admin.model.JobLog;
import com.abyss.orth.admin.scheduler.alarm.JobAlarm;
import com.abyss.orth.admin.scheduler.config.OrthAdminBootstrap;
import com.abyss.orth.admin.util.I18nUtil;
import com.abyss.orth.core.context.OrthJobContext;

import jakarta.mail.internet.MimeMessage;

/**
 * Email-based job failure alarm implementation for the Orth scheduler.
 *
 * <p>This alarm sends formatted HTML email notifications to configured recipients when job failures
 * occur. It includes detailed failure diagnostics in a tabular format for easy readability.
 *
 * <p>Email content includes:
 *
 * <ul>
 *   <li>Job group name
 *   <li>Job ID and description
 *   <li>Execution log ID
 *   <li>Trigger failure details (if applicable)
 *   <li>Execution failure details (if applicable)
 * </ul>
 *
 * <p>Email recipients:
 *
 * <ul>
 *   <li>Configured in {@link JobInfo#getAlarmEmail()}
 *   <li>Multiple recipients supported (comma-separated)
 *   <li>Duplicates automatically removed
 *   <li>Individual send failures logged but don't fail overall alarm
 * </ul>
 *
 * <p>Configuration requirements:
 *
 * <ul>
 *   <li>Spring Mail must be configured with SMTP settings
 *   <li>{@code spring.mail.username} defines the sender address
 *   <li>Mail server credentials must be valid
 * </ul>
 *
 * @author xuxueli 2020-01-19
 */
@Component
public class EmailJobAlarm implements JobAlarm {
    private static final Logger logger = LoggerFactory.getLogger(EmailJobAlarm.class);

    /**
     * Sends email alarm notifications for job failures.
     *
     * <p>This method constructs and sends HTML-formatted alarm emails to all configured recipients.
     * It includes diagnostic information about both trigger and execution failures.
     *
     * <p>Email sending logic:
     *
     * <ul>
     *   <li>Returns true immediately if no alarm email is configured
     *   <li>Builds failure content from trigger and handle messages
     *   <li>Sends individual emails to each recipient (deduplicated)
     *   <li>Logs errors for failed sends but continues with remaining recipients
     *   <li>Returns false if ANY email send fails
     * </ul>
     *
     * @param info the job configuration including alarm email recipients
     * @param jobLog the execution log containing failure details
     * @return true if all emails sent successfully (or no recipients configured), false otherwise
     */
    @Override
    public boolean doAlarm(JobInfo info, JobLog jobLog) {
        // Guard clause: no alarm email configured
        if (!hasAlarmEmail(info)) {
            return true;
        }

        // Build alarm content from failure details
        var alarmContent = buildAlarmContent(jobLog);

        // Load job group for email context
        var group = loadJobGroup(info.getJobGroup());

        // Format email content
        var emailSubject = I18nUtil.getString("jobconf_monitor");
        var emailBody = formatEmailBody(group, info, alarmContent);
        var senderName = I18nUtil.getString("admin_name_full");

        // Send to all recipients (deduplicated)
        var recipients = parseRecipients(info.getAlarmEmail());
        return sendToAllRecipients(recipients, senderName, emailSubject, emailBody, jobLog.getId());
    }

    /** Checks if alarm email is configured for the job. */
    private boolean hasAlarmEmail(JobInfo info) {
        return info != null
                && info.getAlarmEmail() != null
                && !info.getAlarmEmail().trim().isEmpty();
    }

    /** Builds alarm content describing the failure. */
    private String buildAlarmContent(JobLog jobLog) {
        var content = new StringBuilder("Alarm Job LogId=").append(jobLog.getId());

        // Add trigger failure details if present
        if (jobLog.getTriggerCode() != OrthJobContext.HANDLE_CODE_SUCCESS) {
            content.append("<br>TriggerMsg=<br>").append(jobLog.getTriggerMsg());
        }

        // Add execution failure details if present
        if (jobLog.getHandleCode() > 0
                && jobLog.getHandleCode() != OrthJobContext.HANDLE_CODE_SUCCESS) {
            content.append("<br>HandleCode=").append(jobLog.getHandleMsg());
        }

        return content.toString();
    }

    /** Loads the job group for the email context. */
    private JobGroup loadJobGroup(int jobGroupId) {
        return OrthAdminBootstrap.getInstance().getJobGroupMapper().load(jobGroupId);
    }

    /** Formats the email body using the HTML template. */
    private String formatEmailBody(JobGroup group, JobInfo info, String alarmContent) {
        return MessageFormat.format(
                buildEmailTemplate(),
                group != null ? group.getTitle() : "null",
                info.getId(),
                info.getJobDesc(),
                alarmContent);
    }

    /** Parses and deduplicates email recipients from comma-separated list. */
    private Set<String> parseRecipients(String alarmEmail) {
        return new HashSet<>(Arrays.asList(alarmEmail.split(",")));
    }

    /**
     * Sends emails to all recipients.
     *
     * @return true if all sends succeeded, false if any failed
     */
    private boolean sendToAllRecipients(
            Set<String> recipients, String senderName, String subject, String body, long logId) {

        boolean allSucceeded = true;
        var bootstrap = OrthAdminBootstrap.getInstance();

        for (var recipient : recipients) {
            try {
                sendEmail(bootstrap, senderName, recipient, subject, body);
            } catch (Exception e) {
                logger.error(
                        "Orth scheduler email alarm send failed for log {}, recipient {}: {}",
                        logId,
                        recipient,
                        e.getMessage(),
                        e);
                allSucceeded = false;
            }
        }

        return allSucceeded;
    }

    /** Sends a single email. */
    private void sendEmail(
            OrthAdminBootstrap bootstrap,
            String senderName,
            String recipient,
            String subject,
            String body)
            throws Exception {

        MimeMessage mimeMessage = bootstrap.getMailSender().createMimeMessage();
        var helper = new MimeMessageHelper(mimeMessage, true);

        helper.setFrom(bootstrap.getEmailFrom(), senderName);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(body, true);

        bootstrap.getMailSender().send(mimeMessage);
    }

    /**
     * Builds the HTML email template for alarm notifications.
     *
     * <p>The template is an internationalized HTML table with the following columns:
     *
     * <ul>
     *   <li>Job Group
     *   <li>Job ID
     *   <li>Job Description
     *   <li>Alarm Title
     *   <li>Alarm Content (failure details)
     * </ul>
     *
     * <p>The template uses MessageFormat placeholders ({0}, {1}, etc.) that are substituted with
     * actual job information by the caller.
     *
     * @return HTML email template with MessageFormat placeholders
     */
    private static String buildEmailTemplate() {
        return "<h5>"
                + I18nUtil.getString("jobconf_monitor_detail")
                + "：</span>"
                + "<table border=\"1\" cellpadding=\"3\" "
                + "style=\"border-collapse:collapse; width:80%;\" >\n"
                + "   <thead style=\"font-weight: bold;color: #ffffff;"
                + "background-color: #ff8c00;\" >"
                + "      <tr>\n"
                + "         <td width=\"20%\" >"
                + I18nUtil.getString("jobinfo_field_jobgroup")
                + "</td>\n"
                + "         <td width=\"10%\" >"
                + I18nUtil.getString("jobinfo_field_id")
                + "</td>\n"
                + "         <td width=\"20%\" >"
                + I18nUtil.getString("jobinfo_field_jobdesc")
                + "</td>\n"
                + "         <td width=\"10%\" >"
                + I18nUtil.getString("jobconf_monitor_alarm_title")
                + "</td>\n"
                + "         <td width=\"40%\" >"
                + I18nUtil.getString("jobconf_monitor_alarm_content")
                + "</td>\n"
                + "      </tr>\n"
                + "   </thead>\n"
                + "   <tbody>\n"
                + "      <tr>\n"
                + "         <td>{0}</td>\n"
                + "         <td>{1}</td>\n"
                + "         <td>{2}</td>\n"
                + "         <td>"
                + I18nUtil.getString("jobconf_monitor_alarm_type")
                + "</td>\n"
                + "         <td>{3}</td>\n"
                + "      </tr>\n"
                + "   </tbody>\n"
                + "</table>";
    }
}
