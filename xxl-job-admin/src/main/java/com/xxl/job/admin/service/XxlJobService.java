package com.xxl.job.admin.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.xxl.job.admin.model.XxlJobInfo;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.response.PageModel;
import com.xxl.tool.response.Response;

/**
 * core job action for xxl-job
 *
 * @author xuxueli 2016-5-28 15:30:33
 */
public interface XxlJobService {

    /** page list */
    public Response<PageModel<XxlJobInfo>> pageList(
            int offset,
            int pagesize,
            int jobGroup,
            int triggerStatus,
            String jobDesc,
            String executorHandler,
            String author);

    /** add job */
    public Response<String> add(XxlJobInfo jobInfo, LoginInfo loginInfo);

    /** update job */
    public Response<String> update(XxlJobInfo jobInfo, LoginInfo loginInfo);

    /** remove job */
    public Response<String> remove(int id, LoginInfo loginInfo);

    /** start job */
    public Response<String> start(int id, LoginInfo loginInfo);

    /** stop job */
    public Response<String> stop(int id, LoginInfo loginInfo);

    /** trigger */
    public Response<String> trigger(
            LoginInfo loginInfo, int jobId, String executorParam, String addressList);

    /** trigger batch with schedule time range */
    public Response<String> triggerBatch(
            LoginInfo loginInfo,
            int jobId,
            String executorParam,
            String addressList,
            Date startTime,
            Date endTime);

    /** preview trigger batch schedule times */
    public Response<List<String>> previewTriggerBatch(
            LoginInfo loginInfo, int jobId, Date startTime, Date endTime);

    /** dashboard info */
    public Map<String, Object> dashboardInfo();

    /** chart info */
    public Response<Map<String, Object>> chartInfo(Date startDate, Date endDate);
}
