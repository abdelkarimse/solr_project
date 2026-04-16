package solr.solr.repositories.Interface;

import org.apache.solr.client.solrj.SolrServerException;
import solr.solr.data.FileDocument;
import solr.solr.data.FacetedSearchResult;

import java.io.IOException;
import java.util.Map;


public interface ISolrRepository {

    void indexDocument(FileDocument fileDocument) throws SolrServerException, IOException;


    void deleteDocument(String id) throws SolrServerException, IOException;

    FacetedSearchResult searchWithFacets(String query, int limit, String... facetFields) throws SolrServerException, IOException;

    FacetedSearchResult advancedSearch(String query, Map<String, String> fieldFilters, int limit, String... facetFields) 
            throws SolrServerException, IOException;


    FacetedSearchResult listDocuments(int limit, String... facetFields) throws SolrServerException, IOException;
    String generateId();

    long getFileCount() throws SolrServerException, IOException;
    long getTotalSize() throws SolrServerException, IOException;
}
