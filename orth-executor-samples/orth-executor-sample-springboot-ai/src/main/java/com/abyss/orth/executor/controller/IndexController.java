package com.abyss.orth.executor.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.imfangs.dify.client.DifyClientFactory;
import io.github.imfangs.dify.client.DifyWorkflowClient;
import io.github.imfangs.dify.client.callback.WorkflowStreamCallback;
import io.github.imfangs.dify.client.enums.ResponseMode;
import io.github.imfangs.dify.client.event.*;
import io.github.imfangs.dify.client.model.workflow.WorkflowRunRequest;
import io.github.imfangs.dify.client.model.workflow.WorkflowRunResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Index Controller for Orth Job AI Executor.
 *
 * <p>This controller provides HTTP endpoints for testing AI integrations (Ollama and Dify)
 * independently of the job scheduling framework. These endpoints are useful for:
 *
 * <ul>
 *   <li>Validating AI framework configuration before scheduling jobs
 *   <li>Interactive testing and debugging of AI prompts and workflows
 *   <li>Demonstrating AI capabilities to stakeholders
 *   <li>Monitoring AI service health
 * </ul>
 *
 * <h2>Ollama Endpoints:</h2>
 *
 * <ul>
 *   <li>{@code GET /chat/simple?input=query} - Simple blocking chat request
 *   <li>{@code GET /chat/stream?input=query} - Streaming chat with real-time tokens
 * </ul>
 *
 * <h2>Dify Endpoints:</h2>
 *
 * <ul>
 *   <li>{@code GET /dify/simple?input=query} - Blocking workflow execution
 *   <li>{@code GET /dify/stream?input=query} - Streaming workflow with event notifications
 * </ul>
 *
 * <h2>Health Check:</h2>
 *
 * <ul>
 *   <li>{@code GET /} - Returns executor status message
 * </ul>
 *
 * <p><strong>Note:</strong> These endpoints are for testing only. Production AI jobs should use the
 * scheduled job handlers in {@link com.abyss.orth.executor.jobhandler.AIOrthJob} for proper
 * logging, error handling, and result reporting.
 *
 * @author xuxueli 2018-10-28 00:38:13
 * @see com.abyss.orth.executor.jobhandler.AIOrthJob
 */
@Controller
@EnableAutoConfiguration
public class IndexController {
    private static final Logger logger = LoggerFactory.getLogger(IndexController.class);

    private static final String DEFAULT_PROMPT =
            "You are a software engineer, good at solving technical problems.";
    private static final String DEFAULT_MODEL = "qwen3:0.6b";
    private static final String DEFAULT_INPUT = "Introduce yourself";

    private static final String DIFY_BASE_URL = "http://localhost/v1";
    private static final String DIFY_API_KEY = "app-46gHBiqUb5jqAHl9TDWwnRZ8";
    private static final String DIFY_DEFAULT_USER = "user-123";

    @Resource private OllamaChatModel ollamaChatModel;

    /**
     * Health check endpoint.
     *
     * @return executor status message
     */
    @RequestMapping("/")
    @ResponseBody
    String index() {
        return "orth job ai executor running.";
    }

    /**
     * Simple Ollama chat endpoint with blocking response.
     *
     * <p>This endpoint demonstrates basic Ollama chat functionality with:
     *
     * <ul>
     *   <li>Chat memory for conversation context
     *   <li>Request/response logging for debugging
     *   <li>Configurable model selection
     *   <li>System prompt for role definition
     * </ul>
     *
     * <p><strong>Example:</strong> {@code GET /chat/simple?input=What%20is%20Spring%20Boot?}
     *
     * @param input user input query (optional, defaults to "Introduce yourself")
     * @return AI response as plain text
     */
    @GetMapping("/chat/simple")
    @ResponseBody
    public String simpleChat(
            @RequestParam(value = "input", required = false, defaultValue = DEFAULT_INPUT)
                    String input) {

        ChatClient ollamaChatClient = buildOllamaChatClient();
        String response = ollamaChatClient.prompt(DEFAULT_PROMPT).user(input).call().content();

        logger.info("result: " + response);
        return response;
    }

    /**
     * Streaming Ollama chat endpoint with real-time token generation.
     *
     * <p>This endpoint demonstrates streaming chat functionality where tokens are generated
     * incrementally and sent to the client as they become available. This provides:
     *
     * <ul>
     *   <li>Real-time user feedback (tokens appear as they're generated)
     *   <li>Lower perceived latency for long responses
     *   <li>Ability to cancel generation mid-stream
     *   <li>Better user experience for conversational AI
     * </ul>
     *
     * <p><strong>Example:</strong> {@code GET /chat/stream?input=Explain%20microservices}
     *
     * @param response HTTP response for character encoding
     * @param input user input query (optional, defaults to "Introduce yourself")
     * @return Flux stream of response tokens
     */
    @GetMapping("/chat/stream")
    public Flux<String> streamChat(
            HttpServletResponse response,
            @RequestParam(value = "input", required = false, defaultValue = DEFAULT_INPUT)
                    String input) {
        response.setCharacterEncoding("UTF-8");

        ChatClient ollamaChatClient = buildOllamaChatClient();
        return ollamaChatClient.prompt(DEFAULT_PROMPT).user(input).stream().content();
    }

    /**
     * Builds configured Ollama chat client with memory and logging.
     *
     * @return configured ChatClient instance
     */
    private ChatClient buildOllamaChatClient() {
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build())
                                .build())
                .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                .defaultOptions(OllamaChatOptions.builder().model(DEFAULT_MODEL).build())
                .build();
    }

    /**
     * Simple Dify workflow endpoint with blocking response.
     *
     * <p>This endpoint demonstrates Dify workflow execution in blocking mode. The request waits
     * until the entire workflow completes before returning results. This is suitable for:
     *
     * <ul>
     *   <li>Short-running workflows with quick responses
     *   <li>Workflows where intermediate progress isn't needed
     *   <li>Synchronous API integrations
     *   <li>Testing and validation scenarios
     * </ul>
     *
     * <p><strong>Configuration:</strong> Update {@link #DIFY_BASE_URL} and {@link #DIFY_API_KEY} to
     * match your Dify instance.
     *
     * <p><strong>Example:</strong> {@code GET /dify/simple?input=Analyze%20this%20text}
     *
     * @param input user input for workflow (optional)
     * @return JSON response with workflow outputs
     * @throws Exception if workflow execution fails
     */
    @GetMapping("/dify/simple")
    @ResponseBody
    public String difySimple(@RequestParam(required = false, value = "input") String input)
            throws Exception {

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", input);

        WorkflowRunRequest request =
                WorkflowRunRequest.builder()
                        .inputs(inputs)
                        .responseMode(ResponseMode.BLOCKING)
                        .user(DIFY_DEFAULT_USER)
                        .build();

        DifyWorkflowClient workflowClient =
                DifyClientFactory.createWorkflowClient(DIFY_BASE_URL, DIFY_API_KEY);
        WorkflowRunResponse response = workflowClient.runWorkflow(request);

        return serializeToJson(response.getData().getOutputs());
    }

    /**
     * Streaming Dify workflow endpoint with real-time event notifications.
     *
     * <p>This endpoint demonstrates Dify workflow execution in streaming mode. Events are emitted
     * as the workflow progresses through nodes, providing:
     *
     * <ul>
     *   <li>Real-time progress feedback (workflow started, nodes executing, outputs generated)
     *   <li>Visibility into multi-step workflow execution
     *   <li>Intermediate results from each workflow node
     *   <li>Better user experience for long-running workflows
     * </ul>
     *
     * <p><strong>Event Types:</strong>
     *
     * <ul>
     *   <li>Workflow Started: Initial workflow state
     *   <li>Node Started: Individual node begins execution
     *   <li>Node Finished: Node completes with outputs
     *   <li>Workflow Finished: Complete workflow results
     *   <li>Error: Exception or failure details
     * </ul>
     *
     * <p><strong>Example:</strong> {@code GET /dify/stream?input=Process%20this%20data}
     *
     * @param input user input for workflow (optional)
     * @return Flux stream of workflow events
     */
    @GetMapping("/dify/stream")
    public Flux<String> difyStream(@RequestParam(required = false, value = "input") String input) {

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", input);

        WorkflowRunRequest request =
                WorkflowRunRequest.builder()
                        .inputs(inputs)
                        .responseMode(ResponseMode.STREAMING)
                        .user(DIFY_DEFAULT_USER)
                        .build();

        DifyWorkflowClient workflowClient =
                DifyClientFactory.createWorkflowClient(DIFY_BASE_URL, DIFY_API_KEY);

        return Flux.create(
                new Consumer<FluxSink<String>>() {
                    @Override
                    public void accept(FluxSink<String> sink) {
                        try {
                            workflowClient.runWorkflowStream(
                                    request, new DifyStreamCallbackHandler(sink));
                        } catch (Exception e) {
                            sink.error(new RuntimeException("Workflow execution failed", e));
                        }
                    }
                });
    }

    /**
     * Serializes object to JSON string for HTTP response.
     *
     * @param obj object to serialize
     * @return JSON string or "null" if object is null
     */
    private String serializeToJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("JSON serialization failed", e);
            return obj.toString();
        }
    }

    /**
     * Dify Workflow Stream Callback Handler.
     *
     * <p>This handler processes streaming events from Dify workflows and emits formatted messages
     * to the Flux sink for client consumption.
     */
    private class DifyStreamCallbackHandler implements WorkflowStreamCallback {
        private final FluxSink<String> sink;

        public DifyStreamCallbackHandler(FluxSink<String> sink) {
            this.sink = sink;
        }

        @Override
        public void onWorkflowStarted(WorkflowStartedEvent event) {
            sink.next("Workflow started: " + serializeToJson(event.getData()));
        }

        @Override
        public void onNodeStarted(NodeStartedEvent event) {
            sink.next("Node started: " + serializeToJson(event.getData()));
        }

        @Override
        public void onNodeFinished(NodeFinishedEvent event) {
            sink.next("Node finished: " + serializeToJson(event.getData().getOutputs()));
        }

        @Override
        public void onWorkflowFinished(WorkflowFinishedEvent event) {
            sink.next("Workflow finished: " + serializeToJson(event.getData().getOutputs()));
            sink.complete();
        }

        @Override
        public void onError(ErrorEvent event) {
            sink.error(new RuntimeException("Workflow error: " + event.getMessage()));
        }

        @Override
        public void onException(Throwable throwable) {
            sink.error(throwable);
        }
    }
}
