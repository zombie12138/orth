package com.abyss.orth.executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Orth Job AI Executor Spring Boot Application.
 *
 * <p>This is a specialized executor application that demonstrates AI integration patterns with the
 * Orth distributed job scheduling framework. It showcases how to build AI-powered scheduled tasks
 * using popular AI frameworks like Ollama and Dify.
 *
 * <h2>AI Integration Features:</h2>
 *
 * <ul>
 *   <li><strong>Ollama Integration:</strong> Local LLM inference with chat memory and streaming
 *       support
 *   <li><strong>Dify Workflow Integration:</strong> Cloud-based AI workflow orchestration with
 *       blocking and streaming modes
 *   <li><strong>RESTful AI Endpoints:</strong> HTTP endpoints for testing AI capabilities
 *       independently
 *   <li><strong>Job Handler Integration:</strong> Scheduled AI tasks with parameter passing and
 *       result logging
 * </ul>
 *
 * <h2>Use Cases:</h2>
 *
 * <ul>
 *   <li>Scheduled AI-powered data analysis and reporting
 *   <li>Batch AI inference on collected data
 *   <li>Automated content generation and processing
 *   <li>AI-driven monitoring and alerting
 *   <li>Periodic AI model evaluation and testing
 * </ul>
 *
 * <h2>Configuration:</h2>
 *
 * <p>Standard executor configuration in {@code application.properties}:
 *
 * <pre>
 * # Executor config (same as standard executor)
 * xxl.job.admin.addresses=http://localhost:18080/orth-admin
 * xxl.job.executor.appname=orth-executor-ai-sample
 * xxl.job.executor.port=9998
 *
 * # Ollama config
 * spring.ai.ollama.base-url=http://localhost:11434
 * spring.ai.ollama.chat.model=qwen3:0.6b
 *
 * # Dify config (in job parameters)
 * # baseUrl, apiKey passed as job parameters for flexibility
 * </pre>
 *
 * <h2>Available Endpoints:</h2>
 *
 * <ul>
 *   <li>{@code GET /} - Health check endpoint
 *   <li>{@code GET /chat/simple?input=query} - Ollama simple chat
 *   <li>{@code GET /chat/stream?input=query} - Ollama streaming chat
 *   <li>{@code GET /dify/simple?input=query} - Dify blocking workflow
 *   <li>{@code GET /dify/stream?input=query} - Dify streaming workflow
 * </ul>
 *
 * <h2>Job Handlers:</h2>
 *
 * <ul>
 *   <li>{@code ollamaJobHandler} - Scheduled Ollama chat inference
 *   <li>{@code difyWorkflowJobHandler} - Scheduled Dify workflow execution
 * </ul>
 *
 * <h2>Architecture:</h2>
 *
 * <p>This application extends the standard executor with AI capabilities:
 *
 * <ol>
 *   <li>Executor registers with admin and receives job triggers
 *   <li>AI job handlers execute with AI framework integration
 *   <li>Results are logged through standard Orth logging
 *   <li>Execution status reported back to admin
 * </ol>
 *
 * @author xuxueli 2018-10-28 00:38:13
 * @see com.abyss.orth.executor.config.OrthJobConfig
 * @see com.abyss.orth.executor.jobhandler.AIOrthJob
 * @see com.abyss.orth.executor.controller.IndexController
 */
@SpringBootApplication
public class OrthAIExecutorApplication {

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OrthAIExecutorApplication.class, args);
    }
}
