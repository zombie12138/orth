package com.abyss.orth.admin.model;

import java.util.Date;

/**
 * GLUE code version history entity.
 *
 * <p>Tracks version history of dynamically edited job code (GLUE mode) for auditing and rollback.
 */
public class JobLogGlue {

    private int id;
    private int jobId; // Job ID reference
    private String glueType; // GLUE type from GlueTypeEnum
    private String glueSource; // GLUE source code
    private String glueRemark; // Version comment/remark
    private Date addTime; // Creation timestamp
    private Date updateTime; // Last update timestamp

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getGlueType() {
        return glueType;
    }

    public void setGlueType(String glueType) {
        this.glueType = glueType;
    }

    public String getGlueSource() {
        return glueSource;
    }

    public void setGlueSource(String glueSource) {
        this.glueSource = glueSource;
    }

    public String getGlueRemark() {
        return glueRemark;
    }

    public void setGlueRemark(String glueRemark) {
        this.glueRemark = glueRemark;
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
}
