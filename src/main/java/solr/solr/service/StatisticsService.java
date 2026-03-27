package solr.solr.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import solr.solr.repositories.Interface.ISolrRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class StatisticsService {

    private ISolrRepository solrRepository;
    private FileStorageService fileStorageService;

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            long totalDocuments = getTotalIndexedDocuments();
            stats.put("totalDocuments", totalDocuments);

            long fileCount = fileStorageService.getFileCount();
            stats.put("totalFiles", fileCount);

            long totalSize = fileStorageService.getTotalFileSize();
            stats.put("totalSize", formatFileSize(totalSize));
            stats.put("totalSizeBytes", totalSize);

        } catch (Exception e) {
            System.err.println("Error getting statistics: " + e.getMessage());
            stats.put("error", "Could not retrieve statistics");
        }

        return stats;
    }

    private long getTotalIndexedDocuments() throws IOException {
        try {
      
            solr.solr.data.FacetedSearchResult result = null;
            
            return fileStorageService.getFileCount();
        } catch (Exception e) {
            System.err.println("Error getting document count from Solr: " + e.getMessage());
            return 0;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
