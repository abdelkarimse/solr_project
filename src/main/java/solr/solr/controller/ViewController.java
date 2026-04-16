package solr.solr.controller;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import solr.solr.service.Interface.IFileService;
import solr.solr.data.FacetedSearchResult;
import solr.solr.service.StatisticsService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@AllArgsConstructor
public class ViewController {

    private IFileService fileService;
    private StatisticsService statisticsService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "Accueil");
        
        try {
            Map<String, Object> stats = statisticsService.getDashboardStats();
            model.addAttribute("stats", stats);
        } catch (Exception e) {
            System.err.println("Error loading statistics: " + e.getMessage());
            model.addAttribute("stats", new HashMap<>());
        }
        
        return "index";
    }
    @GetMapping("/documents")
    public String listDocuments(Model model,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "titles", required = false) String[] titles,
            @RequestParam(value = "authors", required = false) String[] authors,
            @RequestParam(value = "file_types", required = false) String[] fileTypes,
            @RequestParam(value = "created_dates", required = false) String[] createdDates) {
        model.addAttribute("pageTitle", "Documents");

        try {
            int currentPage = Math.max(page, 1);
            int pageSize = Math.max(size, 1);
            int requestLimit = currentPage * pageSize;

            String query = buildDocumentsQuery(titles, authors, fileTypes, createdDates);
            FacetedSearchResult result = fileService.searchWithFacets(query, requestLimit);

            List<?> allDocuments = result.getDocuments();
            int fromIndex = Math.min((currentPage - 1) * pageSize, allDocuments.size());
            int toIndex = Math.min(fromIndex + pageSize, allDocuments.size());

            model.addAttribute("results", allDocuments.subList(fromIndex, toIndex));
            model.addAttribute("totalCount", result.getTotalCount());
            model.addAttribute("facets", result.getFacets());
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("currentPage", currentPage);

            long totalPages = pageSize == 0 ? 1 : (long) Math.ceil((double) result.getTotalCount() / pageSize);
            totalPages = Math.max(totalPages, 1);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageNumbers", IntStream.rangeClosed(1, (int) totalPages).boxed().collect(Collectors.toList()));

            model.addAttribute("titlesArray", titles != null ? titles : new String[0]);
            model.addAttribute("authorsArray", authors != null ? authors : new String[0]);
            model.addAttribute("fileTypesArray", fileTypes != null ? fileTypes : new String[0]);
            model.addAttribute("createdDatesArray", createdDates != null ? createdDates : new String[0]);
        } catch (Exception e) {
            System.err.println("Error loading documents: " + e.getMessage());
            model.addAttribute("results", Collections.emptyList());
            model.addAttribute("totalCount", 0L);
            model.addAttribute("facets", new HashMap<>());
            model.addAttribute("pageSize", Math.max(size, 1));
            model.addAttribute("currentPage", Math.max(page, 1));
            model.addAttribute("totalPages", 1L);
            model.addAttribute("pageNumbers", List.of(1));
            model.addAttribute("titlesArray", titles != null ? titles : new String[0]);
            model.addAttribute("authorsArray", authors != null ? authors : new String[0]);
            model.addAttribute("fileTypesArray", fileTypes != null ? fileTypes : new String[0]);
            model.addAttribute("createdDatesArray", createdDates != null ? createdDates : new String[0]);
        }
        return "documents";
    }

    @GetMapping("/upload")
    public String upload(Model model) {
        model.addAttribute("pageTitle", "Importer");
        return "upload";
    }

    @GetMapping("/search")
    public String search(Model model,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "author", required = false) String author,
            @RequestParam(value = "filetype", required = false) String filetype,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {

        model.addAttribute("pageTitle", "Recherche");

        boolean hasCriteria = (query != null && !query.trim().isEmpty())
                || (title != null && !title.trim().isEmpty())
                || (author != null && !author.trim().isEmpty())
                || (filetype != null && !filetype.trim().isEmpty());

        if (hasCriteria) {
            model.addAttribute("query", query != null ? query : "");
            model.addAttribute("title", title != null ? title : "");
            model.addAttribute("author", author != null ? author : "");
            model.addAttribute("filetype", filetype != null ? filetype : "");
            model.addAttribute("limit", limit);
            model.addAttribute("streaming", true);
        } else {
            model.addAttribute("results", List.of());
            model.addAttribute("streaming", false);
        }

        return "search";
    }

    @GetMapping("/error")
    public String error(Model model) {
        model.addAttribute("pageTitle", "Erreur");
        return "error";
    }

    private String buildDocumentsQuery(String[] titles, String[] authors, String[] fileTypes, String[] createdDates) {
        List<String> clauses = new ArrayList<>();

        addFieldClause(clauses, "title", titles);
        addFieldClause(clauses, "author", authors);
        addFieldClause(clauses, "filetype", fileTypes);
        addFieldClause(clauses, "created_date", createdDates);

        return clauses.isEmpty() ? "*:*" : "*:* AND " + String.join(" AND ", clauses);
    }

    private void addFieldClause(List<String> clauses, String fieldName, String[] values) {
        if (values == null || values.length == 0) {
            return;
        }

        List<String> filteredValues = Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .map(this::escapeSolrValue)
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.toList());

        if (!filteredValues.isEmpty()) {
            clauses.add(fieldName + ":(" + String.join(" OR ", filteredValues) + ")");
        }
    }

    private String escapeSolrValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
