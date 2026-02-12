// package com.xxl.job.admin.util;
//
// import freemarker.ext.beans.BeansWrapper;
// import freemarker.ext.beans.BeansWrapperBuilder;
// import freemarker.template.Configuration;
// import freemarker.template.TemplateHashModel;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
/// **
// * Freemarker template utility for static model generation.
// *
// * @author xuxueli 2018-01-17 20:37:48
// * @deprecated This utility is deprecated. Use RESTful API controllers with JSON responses
// *             instead of server-side Freemarker templates.
// *             Migration: Replace Freemarker views with Spring REST controllers returning JSON.
// *             For internationalization, use {@link com.xxl.job.admin.util.I18nUtil} directly.
// *             Orth prefers API-first architecture for better frontend flexibility.
// */
// @Deprecated
// public class FtlUtil {
//    private static Logger logger = LoggerFactory.getLogger(FtlUtil.class);
//
//    private static BeansWrapper wrapper = new
// BeansWrapperBuilder(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build();
// //BeansWrapper.getDefaultInstance();
//
//    public static TemplateHashModel generateStaticModel(String packageName) {
//        try {
//            TemplateHashModel staticModels = wrapper.getStaticModels();
//            TemplateHashModel fileStatics = (TemplateHashModel) staticModels.get(packageName);
//            return fileStatics;
//        } catch (Exception e) {
//            logger.error(e.getMessage(), e);
//        }
//        return null;
//    }
//
// }
