package solr.solr.controller;

import lombok.AllArgsConstructor;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import solr.solr.data.FileDocument;
import solr.solr.data.FacetedSearchResult;
import solr.solr.service.Interface.IFileService;
import solr.solr.service.SummaryService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
@AllArgsConstructor
@RequestMapping("/api")
public class FileController {

    private IFileService fileService;
    private SummaryService summaryService;

    @PostMapping("/files")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "File is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            FileDocument indexedDoc = fileService.indexFile(file);

            response.put("success", true);
            response.put("message", "File indexed successfully");
            response.put("document", indexedDoc);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Error reading file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (SolrServerException e) {
            response.put("success", false);
            response.put("message", "Solr server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Unexpected error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping(value = "/search-summary-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter searchSummaryStream(
            @RequestParam(value = "q", required = true) String query,
            @RequestParam(value = "title", required = false, defaultValue = "") String title,
            @RequestParam(value = "author", required = false, defaultValue = "") String author,
            @RequestParam(value = "filetype", required = false, defaultValue = "") String filetype,
            @RequestParam(value = "limit", defaultValue = "20") int limitParam) {

        SseEmitter emitter = new SseEmitter(300_000L); 

        new Thread(() -> {
            try {
                final String searchQuery = query.trim();
                int finalLimit = limitParam <= 0 ? 20 : (limitParam > 100 ? 100 : limitParam);

                emitter.send(SseEmitter.event()
                    .name("start")
                    .data(Map.of(
                        "query", searchQuery,
                        "status", "searching"
                    ))
                    .build());

                FacetedSearchResult result = fileService.searchWithFacets(searchQuery, finalLimit, "filetype");
                List<FileDocument> documents = result.getDocuments();

                if (documents.isEmpty()) {
                    emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(Map.of("message", "No documents found for this query."))
                        .build());
                    emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(Map.of(
                            "status", "done",
                            "documentCount", 0,
                            "totalWords", 0
                        ))
                        .build());
                    emitter.complete();
                    return;
                }

                emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(Map.of(
                        "status", "results_found",
                        "documentCount", documents.size(),
                        "message", "Found " + documents.size() + " document(s)"
                    ))
                    .build());

                for (FileDocument doc : documents) {
                    Map<String, Object> resultData = new HashMap<>();
                    resultData.put("id", doc.getId());
                    resultData.put("name", doc.getName());
                    resultData.put("title", doc.getTitle() != null ? doc.getTitle() : doc.getName());
                    resultData.put("author", doc.getAuthor());
                    resultData.put("content", doc.getContent() != null ? doc.getContent().substring(0, Math.min(200, doc.getContent().length())) : "");
                    resultData.put("fileType", doc.getFileType());
                    resultData.put("size", doc.getSize());
                    resultData.put("uploadDate", doc.getUploadDate());
                    resultData.put("score", doc.getScore());
                    
                    emitter.send(SseEmitter.event()
                        .name("result")
                        .data(resultData)
                        .build());
                    
                    Thread.sleep(10); 
                }

                StringBuilder combinedContent = new StringBuilder();
                for (FileDocument doc : documents) {
                    if (doc.getContent() != null && !doc.getContent().isEmpty()) {
                        if (combinedContent.length() > 0) {
                            combinedContent.append("\n\n");
                        }
                        combinedContent.append("Title: ").append(doc.getTitle() != null ? doc.getTitle() : doc.getName()).append("\n");
                        combinedContent.append(doc.getContent());
                    }
                }

                emitter.send(SseEmitter.event()
                    .name("progress")
                    .data(Map.of(
                        "status", "summarizing",
                        "message", "Generating AI summary..."
                    ))
                    .build());

                String fullSummary = summaryService.generateStreamingSummary(combinedContent.toString());

                String[] words = fullSummary.split("\\s+");
                int wordCount = words.length;

                for (int i = 0; i < wordCount; i++) {
                    emitter.send(SseEmitter.event()
                        .name("word")
                        .data(Map.of(
                            "word", words[i],
                            "index", i,
                            "total", wordCount
                        ))
                        .build());

                    Thread.sleep(50);
                }

                emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(Map.of(
                        "status", "done",
                        "totalWords", wordCount,
                        "documentCount", documents.size()
                    ))
                    .build());

                emitter.complete();

            } catch (SolrServerException e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                            "error", "Solr Backend Error",
                            "message", "Unable to connect to search service: " + e.getMessage(),
                            "type", "SOLR_ERROR"
                        ))
                        .build());
                } catch (IOException ignored) {}
                emitter.completeWithError(e);

            } catch (IOException e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                            "error", "I/O Error",
                            "message", "Error reading files: " + e.getMessage(),
                            "type", "IO_ERROR"
                        ))
                        .build());
                } catch (IOException ignored) {}
                emitter.completeWithError(e);

            } catch (InterruptedException e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                            "error", "Request Interrupted",
                            "message", "Summary generation was interrupted",
                            "type", "INTERRUPTED"
                        ))
                        .build());
                } catch (IOException ignored) {}
                emitter.completeWithError(e);

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of(
                            "error", "Unexpected Error",
                            "message", e.getClass().getSimpleName() + ": " + e.getMessage(),
                            "type", "UNEXPECTED_ERROR"
                        ))
                        .build());
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

}
