package solr.solr.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacetedSearchResult {
    
    private List<FileDocument> documents;
    
    private long totalCount;
    
    private Map<String, List<FacetValue>> facets;
    
    private Float queryTime;
}
