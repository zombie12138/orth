package com.xxl.job.executor.jobhandler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.tool.gson.GsonTool;

import io.github.imfangs.dify.client.DifyClientFactory;
import io.github.imfangs.dify.client.DifyWorkflowClient;
import io.github.imfangs.dify.client.enums.ResponseMode;
import io.github.imfangs.dify.client.model.workflow.WorkflowRunRequest;
import io.github.imfangs.dify.client.model.workflow.WorkflowRunResponse;
import jakarta.annotation.Resource;

/**
 * AI Job Handler Demonstrations.
 *
 * <p>This class demonstrates scheduled AI task execution with Orth job framework integration. These
 * handlers combine AI capabilities (Ollama LLM, Dify workflows) with distributed job scheduling,
 * logging, and error handling.
 *
 * <h2>AI Job Handler Types:</h2>
 *
 * <ul>
 *   <li><strong>Ollama Job:</strong> Scheduled local LLM inference with configurable prompts
 *   <li><strong>Dify Workflow Job:</strong> Scheduled cloud-based AI workflow orchestration
 * </ul>
 *
 * <h2>Use Cases:</h2>
 *
 * <ul>
 *   <li>Periodic batch AI inference on collected data
 *   <li>Scheduled AI-powered report generation
 *   <li>Automated content summarization and analysis
 *   <li>Regular AI model evaluation and quality checks
 *   <li>AI-driven anomaly detection on monitoring data
 * </ul>
 *
 * <h2>Parameter Format:</h2>
 *
 * <p>All AI job parameters are passed as JSON strings via job configuration in admin. Parameters
 * should be configured in the "Job Parameters" field when creating jobs.
 *
 * <h2>Logging:</h2>
 *
 * <p>All job output (inputs, AI responses, errors) is logged through {@code XxlJobHelper.log()} and
 * persisted to executor log files. Logs are accessible through the admin console for each
 * execution.
 *
 * <h2>Error Handling:</h2>
 *
 * <p>Failures are reported via {@code XxlJobHelper.handleFail()} and marked as FAIL status in
 * admin. This triggers alerts and allows retry/recovery based on job configuration.
 *
 * @author xuxueli 2025-04-06
 * @see XxlJobHelper
 * @see com.xxl.job.core.handler.annotation.XxlJob
 */
@Component
public class AIXxlJob {

    private static final String DEFAULT_PROMPT =
            "You are a software engineer, good at solving technical problems.";
    private static final String DEFAULT_MODEL = "qwen3:0.6b";
    private static final String DEFAULT_USER = "orth-job";

    @Resource private OllamaChatModel ollamaChatModel;

    /**
     * Ollama Chat job handler for scheduled LLM inference.
     *
     * <p>This job executes Ollama-based LLM inference on a schedule. It's useful for:
     *
     * <ul>
     *   <li>Batch processing of text data with AI analysis
     *   <li>Scheduled content generation and summarization
     *   <li>Periodic AI-driven data enrichment
     *   <li>Automated question answering on collected data
     * </ul>
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="ollamaJobHandler"
     *
     * <p><strong>Parameters (required JSON):</strong>
     *
     * <pre>
     * {
     *   "input": "User query or text to process",          // Required
     *   "prompt": "System prompt for AI role definition",  // Optional (defaults to engineer role)
     *   "model": "qwen3:0.6b"                              // Optional (defaults to qwen3:0.6b)
     * }
     * </pre>
     *
     * <p><strong>Example Parameters:</strong>
     *
     * <pre>
     * Simple query:
     * {
     *   "input": "Summarize the latest system logs"
     * }
     *
     * Custom prompt and model:
     * {
     *   "input": "Analyze this error message: NullPointerException at line 42",
     *   "prompt": "You are a debugging expert. Analyze errors and suggest fixes.",
     *   "model": "qwen3:0.6b"
     * }
     * </pre>
     *
     * <p><strong>Configuration:</strong>
     *
     * <p>Ollama must be running and accessible at the configured base URL:
     *
     * <pre>
     * spring.ai.ollama.base-url=http://localhost:11434
     * </pre>
     *
     * <p><strong>Execution Flow:</strong>
     *
     * <ol>
     *   <li>Parse and validate job parameters
     *   <li>Apply default values for optional fields
     *   <li>Build ChatClient with memory and logging advisors
     *   <li>Execute LLM inference with configured prompt and input
     *   <li>Log input and AI response to job logs
     *   <li>Report SUCCESS or FAIL status to admin
     * </ol>
     */
    @XxlJob("ollamaJobHandler")
    public void ollamaJobHandler() {
        String param = XxlJobHelper.getJobParam();

        if (isParameterEmpty(param)) {
            XxlJobHelper.log("param is empty.");
            XxlJobHelper.handleFail();
            return;
        }

        OllamaParam ollamaParam = parseOllamaParam(param);
        if (ollamaParam == null) {
            return;
        }

        applyOllamaDefaults(ollamaParam);

        if (!validateOllamaParam(ollamaParam)) {
            return;
        }

        XxlJobHelper.log("<br><br><b>【Input】: " + ollamaParam.getInput() + "</b><br><br>");

        String response = executeOllamaInference(ollamaParam);

        XxlJobHelper.log("<br><br><b>【Output】: " + response + "</b><br><br>");
    }

    /**
     * Checks if parameter is empty.
     *
     * @param param parameter string to validate
     * @return true if parameter is null or empty
     */
    private boolean isParameterEmpty(String param) {
        return param == null || param.trim().isEmpty();
    }

    /**
     * Parses JSON parameter into OllamaParam object.
     *
     * @param param JSON string to parse
     * @return parsed OllamaParam or null if parsing fails
     */
    private OllamaParam parseOllamaParam(String param) {
        try {
            return GsonTool.fromJson(param, OllamaParam.class);
        } catch (Exception e) {
            XxlJobHelper.log(new RuntimeException("OllamaParam parse error", e));
            XxlJobHelper.handleFail();
            return null;
        }
    }

    /**
     * Applies default values to Ollama parameters.
     *
     * @param ollamaParam parameters to modify
     */
    private void applyOllamaDefaults(OllamaParam ollamaParam) {
        if (ollamaParam.getPrompt() == null || ollamaParam.getPrompt().isBlank()) {
            ollamaParam.setPrompt(DEFAULT_PROMPT);
        }
        if (ollamaParam.getModel() == null || ollamaParam.getModel().isBlank()) {
            ollamaParam.setModel(DEFAULT_MODEL);
        }
    }

    /**
     * Validates Ollama parameters.
     *
     * @param ollamaParam parameters to validate
     * @return true if parameters are valid
     */
    private boolean validateOllamaParam(OllamaParam ollamaParam) {
        if (ollamaParam.getInput() == null || ollamaParam.getInput().isBlank()) {
            XxlJobHelper.log("input is empty.");
            XxlJobHelper.handleFail();
            return false;
        }
        return true;
    }

    /**
     * Executes Ollama LLM inference.
     *
     * @param ollamaParam inference parameters
     * @return AI response text
     */
    private String executeOllamaInference(OllamaParam ollamaParam) {
        ChatClient ollamaChatClient =
                ChatClient.builder(ollamaChatModel)
                        .defaultAdvisors(
                                MessageChatMemoryAdvisor.builder(
                                                MessageWindowChatMemory.builder().build())
                                        .build())
                        .defaultAdvisors(SimpleLoggerAdvisor.builder().build())
                        .defaultOptions(
                                OllamaChatOptions.builder().model(ollamaParam.getModel()).build())
                        .build();

        return ollamaChatClient
                .prompt(ollamaParam.getPrompt())
                .user(ollamaParam.getInput())
                .call()
                .content();
    }

    /**
     * Ollama Job Parameter Model.
     *
     * <p>Encapsulates Ollama LLM inference configuration.
     */
    private static class OllamaParam {
        private String input;
        private String prompt;
        private String model;

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    /**
     * Dify Workflow job handler for scheduled AI workflow orchestration.
     *
     * <p>This job executes Dify-based AI workflows on a schedule. Dify workflows are multi-step AI
     * pipelines that can include LLMs, tools, knowledge bases, and custom logic. This is useful
     * for:
     *
     * <ul>
     *   <li>Complex AI pipelines with multiple processing stages
     *   <li>Knowledge base-enhanced AI responses
     *   <li>Multi-agent AI collaboration scenarios
     *   <li>AI workflows with tool calling and external integrations
     * </ul>
     *
     * <p><strong>Usage:</strong> Create a job in admin with JobHandler="difyWorkflowJobHandler"
     *
     * <p><strong>Parameters (required JSON):</strong>
     *
     * <pre>
     * {
     *   "baseUrl": "http://dify-server/v1",                      // Required: Dify API base URL
     *   "apiKey": "app-xxx",                                     // Required: Dify app API key
     *   "inputs": {                                              // Required: Workflow input variables
     *     "input": "User query",                                 // Example variable (workflow-specific)
     *     "param1": "value1"                                     // Additional variables as needed
     *   },
     *   "user": "user-identifier"                                // Optional: User ID (defaults to "orth-job")
     * }
     * </pre>
     *
     * <p><strong>Workflow Input Variables:</strong>
     *
     * <p>The {@code inputs} map contains workflow-specific variables defined in your Dify
     * workflow's "Start" node. Variable names and types must match your workflow configuration.
     *
     * <p><strong>Example Parameters:</strong>
     *
     * <pre>
     * Simple workflow:
     * {
     *   "baseUrl": "http://localhost/v1",
     *   "apiKey": "app-46gHBiqUb5jqAHl9TDWwnRZ8",
     *   "inputs": {
     *     "input": "Analyze customer feedback from last week"
     *   }
     * }
     *
     * Multi-variable workflow:
     * {
     *   "baseUrl": "http://dify.example.com/v1",
     *   "apiKey": "app-production-key",
     *   "inputs": {
     *     "query": "Generate monthly report",
     *     "format": "pdf",
     *     "language": "en"
     *   },
     *   "user": "batch-job-001"
     * }
     * </pre>
     *
     * <p><strong>Security Note:</strong>
     *
     * <p>API keys should be managed securely. Consider:
     *
     * <ul>
     *   <li>Using environment variables or secrets management
     *   <li>Encrypting sensitive parameters in job configuration
     *   <li>Rotating API keys regularly
     *   <li>Using dedicated service accounts for batch jobs
     * </ul>
     *
     * <p><strong>Execution Flow:</strong>
     *
     * <ol>
     *   <li>Parse and validate job parameters
     *   <li>Apply default values for optional fields
     *   <li>Build Dify workflow request with inputs
     *   <li>Execute workflow in blocking mode (waits for completion)
     *   <li>Log inputs and workflow outputs to job logs
     *   <li>Report SUCCESS or FAIL status to admin
     * </ol>
     *
     * @throws Exception if workflow execution fails
     */
    @XxlJob("difyWorkflowJobHandler")
    public void difyWorkflowJobHandler() throws Exception {
        String param = XxlJobHelper.getJobParam();

        if (isParameterEmpty(param)) {
            XxlJobHelper.log("param is empty.");
            XxlJobHelper.handleFail();
            return;
        }

        DifyParam difyParam = parseDifyParam(param);
        if (difyParam == null) {
            return;
        }

        applyDifyDefaults(difyParam);

        if (!validateDifyParam(difyParam)) {
            return;
        }

        XxlJobHelper.log("<br><br><b>【inputs】: " + difyParam.getInputs() + "</b><br><br>");

        WorkflowRunResponse response = executeDifyWorkflow(difyParam);

        XxlJobHelper.log(
                "<br><br><b>【Output】: " + response.getData().getOutputs() + "</b><br><br>");
    }

    /**
     * Parses JSON parameter into DifyParam object.
     *
     * @param param JSON string to parse
     * @return parsed DifyParam or null if parsing fails
     */
    private DifyParam parseDifyParam(String param) {
        try {
            return GsonTool.fromJson(param, DifyParam.class);
        } catch (Exception e) {
            XxlJobHelper.log(new RuntimeException("DifyParam parse error", e));
            XxlJobHelper.handleFail();
            return null;
        }
    }

    /**
     * Applies default values to Dify parameters.
     *
     * @param difyParam parameters to modify
     */
    private void applyDifyDefaults(DifyParam difyParam) {
        if (difyParam.getInputs() == null) {
            difyParam.setInputs(new HashMap<>());
        }
        if (difyParam.getUser() == null) {
            difyParam.setUser(DEFAULT_USER);
        }
    }

    /**
     * Validates Dify parameters.
     *
     * @param difyParam parameters to validate
     * @return true if parameters are valid
     */
    private boolean validateDifyParam(DifyParam difyParam) {
        if (difyParam.getBaseUrl() == null || difyParam.getApiKey() == null) {
            XxlJobHelper.log("baseUrl or apiKey invalid.");
            XxlJobHelper.handleFail();
            return false;
        }
        return true;
    }

    /**
     * Executes Dify workflow in blocking mode.
     *
     * @param difyParam workflow parameters
     * @return workflow execution response
     * @throws Exception if workflow execution fails
     */
    private WorkflowRunResponse executeDifyWorkflow(DifyParam difyParam) throws Exception {
        WorkflowRunRequest request =
                WorkflowRunRequest.builder()
                        .inputs(difyParam.getInputs())
                        .responseMode(ResponseMode.BLOCKING)
                        .user(difyParam.getUser())
                        .build();

        DifyWorkflowClient workflowClient =
                DifyClientFactory.createWorkflowClient(
                        difyParam.getBaseUrl(), difyParam.getApiKey());
        return workflowClient.runWorkflow(request);
    }

    /**
     * Dify Workflow Job Parameter Model.
     *
     * <p>Encapsulates Dify workflow execution configuration.
     */
    private static class DifyParam {
        private Map<String, Object> inputs;
        private String user;
        private String baseUrl;
        private String apiKey;

        public Map<String, Object> getInputs() {
            return inputs;
        }

        public void setInputs(Map<String, Object> inputs) {
            this.inputs = inputs;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
