package solr.solr.repositories;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import solr.solr.data.FileDocument;
import solr.solr.data.FacetValue;
import solr.solr.data.FacetedSearchResult;
import solr.solr.repositories.Interface.ISolrRepository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class SolrRepository implements ISolrRepository {

    private SolrClient solrClient;

    public  SolrRepository(SolrClient ss){
        this.solrClient =ss ;
        }

    @Value("${solr.host}")
    private String solrHost;

    private static final String CORE_NAME = "Projects_Solr";

    public void indexDocument(FileDocument fileDocument) throws SolrServerException, IOException {
        SolrInputDocument fullDoc = new SolrInputDocument();
        fullDoc.addField("id", fileDocument.getId());
        fullDoc.addField("name", fileDocument.getName());
        fullDoc.addField("title", fileDocument.getTitle());
        fullDoc.addField("author", fileDocument.getAuthor());
        fullDoc.addField("content", fileDocument.getContent());
        fullDoc.addField("filetype", fileDocument.getFileType());
        fullDoc.addField("size", fileDocument.getSize());
        fullDoc.addField("upload_date", fileDocument.getUploadDate());
        fullDoc.addField("created_date", fileDocument.getCreatedDate());
        fullDoc.addField("description", fileDocument.getDescription());

        try {
            solrClient.add(CORE_NAME, fullDoc);
            solrClient.commit(CORE_NAME);
        } catch (SolrServerException ex) {
            String errorMessage = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (!errorMessage.contains("undefined field") && !errorMessage.contains("unknown field")) {
                throw ex;
            }

            SolrInputDocument fallbackDoc = new SolrInputDocument();
            fallbackDoc.addField("id", fileDocument.getId());
            fallbackDoc.addField("name", fileDocument.getName());
            fallbackDoc.addField("content", fileDocument.getContent());
            fallbackDoc.addField("filetype", fileDocument.getFileType());
            fallbackDoc.addField("size", fileDocument.getSize());
            fallbackDoc.addField("upload_date", fileDocument.getUploadDate());
            fallbackDoc.addField("description", fileDocument.getDescription());

            solrClient.add(CORE_NAME, fallbackDoc);
            solrClient.commit(CORE_NAME);
        }
    }


       public void deleteDocument(String id) throws SolrServerException, IOException {
        solrClient.deleteById(CORE_NAME, id);
        solrClient.commit(CORE_NAME);
    }

    @Override
    public FacetedSearchResult searchWithFacets(String query, int limit, String... facetFields) 
            throws SolrServerException, IOException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setRows(limit);
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", "name title content description author");
        solrQuery.set("fl", "id,name,title,author,content,filetype,size,upload_date,created_date,description,score");
        
        solrQuery.setFacet(true);
        for (String field : facetFields) {
            solrQuery.addFacetField(field);
        }
        solrQuery.setFacetLimit(20);
        solrQuery.setFacetMinCount(1);

        QueryResponse response = solrClient.query(CORE_NAME, solrQuery);
        SolrDocumentList results = response.getResults();

        List<FileDocument> documents = new ArrayList<>();
        for (SolrDocument doc : results) {
            FileDocument fileDoc = FileDocument.builder()
                    .id(getStringField(doc, "id"))
                    .name(getStringField(doc, "name"))
                    .title(getStringField(doc, "title"))
                    .author(getStringField(doc, "author"))
                    .content(getStringField(doc, "content"))
                    .fileType(getStringField(doc, "filetype"))
                    .size(getLongField(doc, "size"))
                    .uploadDate(getDateField(doc, "upload_date"))
                    .createdDate(getDateField(doc, "created_date"))
                    .description(getStringField(doc, "description"))
                    .score(getFloatField(doc, "score"))
                    .build();
            documents.add(fileDoc);
        }

        // Extract facet information
        Map<String, List<FacetValue>> facets = new HashMap<>();
        if (response.getFacetFields() != null) {
            for (FacetField facetField : response.getFacetFields()) {
                List<FacetValue> facetValues = facetField.getValues().stream()
                        .map(count -> FacetValue.builder()
                                .value(count.getName())
                                .count(count.getCount())
                                .build())
                        .collect(Collectors.toList());
                facets.put(facetField.getName(), facetValues);
            }
        }

        return FacetedSearchResult.builder()
                .documents(documents)
                .totalCount(results.getNumFound())
                .facets(facets)
                .queryTime((float) response.getQTime() / 1000f)
                .build();
    }

    @Override
    public FacetedSearchResult advancedSearch(String query, Map<String, String> fieldFilters, int limit, String... facetFields) 
            throws SolrServerException, IOException {
        SolrQuery solrQuery = new SolrQuery();
        
        StringBuilder queryBuilder = new StringBuilder(query);
        if (fieldFilters != null && !fieldFilters.isEmpty()) {
            for (Map.Entry<String, String> entry : fieldFilters.entrySet()) {
                queryBuilder.append(" AND ").append(entry.getKey()).append(":(").append(entry.getValue()).append(")");
            }
        }
        
        solrQuery.setQuery(queryBuilder.toString());
        solrQuery.setRows(limit);
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", "name title content description author");
        solrQuery.set("fl", "id,name,title,author,content,filetype,size,upload_date,created_date,description,score");
        
        // Add faceting
        solrQuery.setFacet(true);
        for (String field : facetFields) {
            solrQuery.addFacetField(field);
        }
        solrQuery.setFacetLimit(20);
        solrQuery.setFacetMinCount(1);

        QueryResponse response = solrClient.query(CORE_NAME, solrQuery);
        SolrDocumentList results = response.getResults();

        List<FileDocument> documents = new ArrayList<>();
        for (SolrDocument doc : results) {
            FileDocument fileDoc = FileDocument.builder()
                    .id(getStringField(doc, "id"))
                    .name(getStringField(doc, "name"))
                    .title(getStringField(doc, "title"))
                    .author(getStringField(doc, "author"))
                    .content(getStringField(doc, "content"))
                    .fileType(getStringField(doc, "filetype"))
                    .size(getLongField(doc, "size"))
                    .uploadDate(getDateField(doc, "upload_date"))
                    .createdDate(getDateField(doc, "created_date"))
                    .description(getStringField(doc, "description"))
                    .score(getFloatField(doc, "score"))
                    .build();
            documents.add(fileDoc);
        }

        Map<String, List<FacetValue>> facets = new HashMap<>();
        if (response.getFacetFields() != null) {
            for (FacetField facetField : response.getFacetFields()) {
                List<FacetValue> facetValues = facetField.getValues().stream()
                        .map(count -> FacetValue.builder()
                                .value(count.getName())
                                .count(count.getCount())
                                .build())
                        .collect(Collectors.toList());
                facets.put(facetField.getName(), facetValues);
            }
        }

        return FacetedSearchResult.builder()
                .documents(documents)
                .totalCount(results.getNumFound())
                .facets(facets)
                .queryTime((float) response.getQTime() / 1000f)
                .build();
    }

    public String generateId() {
        return UUID.randomUUID().toString();
    }


    private String getStringField(SolrDocument doc, String fieldName) {
        Object value = doc.getFieldValue(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.isEmpty() ? null : String.valueOf(list.get(0));
        }
        return String.valueOf(value);
    }

    private Long getLongField(SolrDocument doc, String fieldName) {
        Object value = doc.getFieldValue(fieldName);
        if (value == null) {
            return 0L;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return 0L;
            }
            Object firstElement = list.get(0);
            if (firstElement instanceof Number) {
                return ((Number) firstElement).longValue();
            }
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private Float getFloatField(SolrDocument doc, String fieldName) {
        Object value = doc.getFieldValue(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return null;
            }
            Object firstElement = list.get(0);
            if (firstElement instanceof Number) {
                return ((Number) firstElement).floatValue();
            }
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return null;
    }

    private java.util.Date getDateField(SolrDocument doc, String fieldName) {
        Object value = doc.getFieldValue(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return null;
            }
            Object firstElement = list.get(0);
            if (firstElement instanceof java.util.Date) {
                return (java.util.Date) firstElement;
            }
            return null;
        }
        if (value instanceof java.util.Date) {
            return (java.util.Date) value;
        }
        return null;
    }
}
