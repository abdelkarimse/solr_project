package solr.solr.controller;

import lombok.AllArgsConstructor;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import solr.solr.data.FacetedSearchResult;
import solr.solr.service.Interface.IFileService;
import solr.solr.service.StatisticsService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        
        if (query != null && !query.trim().isEmpty()) {
            model.addAttribute("query", query);
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
}
