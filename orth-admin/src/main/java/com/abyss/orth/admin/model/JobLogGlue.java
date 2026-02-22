package com.abyss.orth.admin.model;

import java.util.Date;

import lombok.Data;

/**
 * GLUE code version history entity.
 *
 * <p>Tracks version history of dynamically edited job code (GLUE mode) for auditing and rollback.
 */
@Data
public class JobLogGlue {

    private int id;
    private int jobId; // Job ID reference
    private String glueType; // GLUE type from GlueTypeEnum
    private String glueSource; // GLUE source code
    private String glueRemark; // Version comment/remark
    private Date addTime; // Creation timestamp
    private Date updateTime; // Last update timestamp
}
