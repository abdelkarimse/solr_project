package solr.solr.service.Interface;

import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.web.multipart.MultipartFile;
import solr.solr.data.FileDocument;
import solr.solr.data.FacetedSearchResult;

import java.io.IOException;
import java.util.Map;


public interface IFileService {


    FileDocument indexFile(MultipartFile file) throws IOException, SolrServerException;

    FacetedSearchResult searchWithFacets(String query, int limit, String... facetFields) throws SolrServerException, IOException;

    FacetedSearchResult advancedSearch(String query, Map<String, String> fieldFilters, int limit, String... facetFields) 
            throws SolrServerException, IOException;

    void deleteFile(String id) throws SolrServerException, IOException;
}
