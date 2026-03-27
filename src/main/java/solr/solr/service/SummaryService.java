package solr.solr.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Slf4j
@Service
public class SummaryService {

    private static final int MAX_CONTENT_LENGTH = 5_000;
    private static final int MAX_SUMMARY_CHARS  = 500;
    private static final int MAX_SUMMARY_TOKENS = 4096;

    @Value("${nvidia.api.key:}")
    private String nvidiaApiKey;

    @Value("${nvidia.api.url:https://integrate.api.nvidia.com/v1/chat/completions}")
    private String nvidiaApiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SummaryService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (isApiConfigured()) {
            log.info("NVIDIA API configured. Key prefix: {}***",
                    nvidiaApiKey.substring(0, Math.min(10, nvidiaApiKey.length())));
        } else {
            log.warn("NVIDIA_API_KEY not set — summaries will use content-truncation fallback.");
        }
    }

    public String generateStreamingSummary(String allDocumentsContent) throws Exception {
        if (allDocumentsContent == null || allDocumentsContent.isBlank()) {
            return "No content available to summarize.";
        }

        String content = truncate(allDocumentsContent, MAX_CONTENT_LENGTH);
        log.debug("Generating summary for content of {} characters", content.length());

        if (!isApiConfigured()) {
            log.info("NVIDIA API not configured, using local fallback summary");
            return generateOverviewFromContent(content, MAX_SUMMARY_CHARS);
        }

        try {
            log.debug("Calling NVIDIA API for summary...");
            String response = callNvidiaApi(content);
            String summary = extractSummaryOrFallback(response, content);
            log.debug("Generated summary of {} characters", summary.length());
            return summary;
        } catch (Exception e) {
            log.error("NVIDIA API call failed, falling back to local summary: {}", e.getMessage());
            return generateOverviewFromContent(content, MAX_SUMMARY_CHARS);
        }
    }

    private boolean isApiConfigured() {
        return nvidiaApiKey != null && !nvidiaApiKey.isBlank();
    }

    private String callNvidiaApi(String content) throws Exception {
        HttpEntity<String> entity = buildHttpEntity(buildRequestBody(content));
        return restTemplate.postForObject(nvidiaApiUrl, entity, String.class);
    }

    private Map<String, Object> buildRequestBody(String content) {
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(content);
        
        return Map.of(
                "model",             "openai/gpt-oss-120b",
                "messages",          new Object[]{
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                },
                "temperature",       0.7,
                "top_p",             0.9,
                "frequency_penalty", 0.0,
                "presence_penalty",  0.0,
                "max_tokens",        MAX_SUMMARY_TOKENS,
                "stream",            false,
                "reasoning_effort",  "medium"
        );
    }

    private String buildSystemPrompt() {
        return "You are an expert document analyst and summarizer. Your task is to provide clear, concise, " +
               "and informative summaries of documents. Follow these guidelines:\n" +
               "1. **Clarity**: Use simple, direct language that anyone can understand.\n" +
               "2. **Accuracy**: Only summarize what is explicitly stated in the documents.\n" +
               "3. **Structure**: Organize key information logically with main topics first.\n" +
               "4. **Brevity**: Be concise while preserving essential information.\n" +
               "5. **Completeness**: Cover the main themes, purposes, and key findings.\n" +
               "6. **No Speculation**: Do not add interpretations or opinions not present in source material.\n" +
               "7. **Format**: Write in paragraph form, not bullet points.\n" +
               "Generate a 2-4 sentence executive summary that captures the essence of the documents.";
    }

    private String buildUserPrompt(String content) {
        return "Please analyze and summarize the following documents. Focus on the main themes, " +
               "key points, and important findings. Provide a clear and concise summary that a reader " +
               "can quickly understand:\n\n" +
               "---\n" +
               content +
               "\n---\n" +
               "\nProvide a comprehensive but brief summary (2-4 sentences maximum).";
    }

    private HttpEntity<String> buildHttpEntity(Map<String, Object> body) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + nvidiaApiKey);
        headers.set("Accept", "application/json");
        return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
    }

    private String extractSummaryOrFallback(String response, String content) throws Exception {
        if (response == null || response.isBlank()) {
            log.debug("Empty response from NVIDIA API, using fallback");
            return generateOverviewFromContent(content, MAX_SUMMARY_CHARS);
        }
        try {
            String summary = objectMapper.readTree(response)
                    .at("/choices/0/message/content")
                    .asText("");
            if (summary.isBlank()) {
                log.debug("No content in API response, using fallback");
                return generateOverviewFromContent(content, MAX_SUMMARY_CHARS);
            }
            return summary.trim();
        } catch (Exception e) {
            log.warn("Error parsing API response: {}, using fallback", e.getMessage());
            return generateOverviewFromContent(content, MAX_SUMMARY_CHARS);
        }
    }

    private String generateOverviewFromContent(String content, int maxChars) {
        if (content == null || content.isBlank()) {
            return "No content available to summarize.";
        }
        
        if (content.length() <= maxChars) {
            return content.trim();
        }

        String[] sentences = content.split("(?<=[.!?])\\s+");
        StringBuilder overview = new StringBuilder();
        int sentencesUsed = 0;
        int maxSentences = 3;

        for (String sentence : sentences) {
            if (sentencesUsed >= maxSentences || overview.length() >= maxChars) {
                break;
            }
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty()) {
                // Ensure sentence ends with punctuation
                if (!trimmed.matches(".*[.!?]$")) {
                    trimmed += ".";
                }
                overview.append(trimmed).append(" ");
                sentencesUsed++;
            }
        }

        String result = overview.toString().trim();
        if (result.length() > maxChars) {
            result = result.substring(0, maxChars).trim();
            if (!result.endsWith(".")) {
                result += "…";
            }
        }
        return result.isEmpty() ? content.trim() : result;
    }

    private static String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}