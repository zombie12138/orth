// package com.xxl.job.executor.controller;
//
// import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
// import org.springframework.stereotype.Controller;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.ResponseBody;
//
// /**
//  * Index Controller for Orth Job Executor.
//  *
//  * <p>This controller provides a simple health check endpoint to verify that the executor is
//  * running. It is disabled by default to minimize the application's HTTP surface area.
//  *
//  * <h2>Usage:</h2>
//  *
//  * <p>Uncomment this class to enable the health check endpoint:
//  *
//  * <pre>
//  * GET http://localhost:9999/
//  * Response: "orth job executor running."
//  * </pre>
//  *
//  * <p>This is useful for:
//  *
//  * <ul>
//  *   <li>Container orchestration health checks (Kubernetes liveness/readiness probes)
//  *   <li>Load balancer health monitoring
//  *   <li>Manual verification during development
//  * </ul>
//  *
//  * <p><strong>Note:</strong> The executor's embedded Netty server (for job triggers) runs
//  * independently on the configured {@code xxl.job.executor.port} and does not require this
//  * controller to be enabled.
//  *
//  * @author xuxueli 2018-10-28 00:38:13
//  */
// @Controller
// @EnableAutoConfiguration
// public class IndexController {
//
//    @RequestMapping("/")
//    @ResponseBody
//    String index() {
//        return "orth job executor running.";
//    }
// }
