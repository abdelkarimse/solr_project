package solr.solr.repositories;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
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

    private final SolrClient solrClient;

    private static final String   CORE_NAME           = "Projects_Solr";
    private static final int      FACET_LIMIT         = 20;
    private static final int      FACET_MIN_COUNT     = 1;
    private static final String[] DEFAULT_FACET_FIELDS = {"author", "title", "filetype", "created_date"};

    public SolrRepository(SolrClient solrClient) {
        this.solrClient = solrClient;
    }


    @Override
    public void indexDocument(FileDocument fileDocument) throws SolrServerException, IOException {
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("id",           fileDocument.getId());
        doc.addField("name",         fileDocument.getName());
        doc.addField("title",        fileDocument.getTitle());
        doc.addField("author",       fileDocument.getAuthor());
        doc.addField("content",      fileDocument.getContent());
        doc.addField("filetype",     fileDocument.getFileType());
        doc.addField("size",         fileDocument.getSize());
        doc.addField("upload_date",  fileDocument.getUploadDate());
        doc.addField("created_date", fileDocument.getCreatedDate());
        doc.addField("description",  fileDocument.getDescription());

        try {
            solrClient.add(CORE_NAME, doc);
            solrClient.commit(CORE_NAME);
        } catch (SolrServerException ex) {
            String error = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (error.contains("undefined field") || error.contains("unknown field")) {
                    SolrInputDocument fallback = new SolrInputDocument();
                fallback.addField("id",      fileDocument.getId());
                fallback.addField("name",    fileDocument.getName());
                fallback.addField("content", fileDocument.getContent());
                solrClient.add(CORE_NAME, fallback);
                solrClient.commit(CORE_NAME);
            } else {
                throw ex;
            }
        }
    }


    @Override
    public void deleteDocument(String id) throws SolrServerException, IOException {
        solrClient.deleteById(CORE_NAME, id);
        solrClient.commit(CORE_NAME);
    }


    @Override
    public FacetedSearchResult searchWithFacets(String query, int limit, String... facetFields)
            throws SolrServerException, IOException {
        SolrQuery solrQuery = buildBaseQuery(query, limit, facetFields);
        QueryResponse response = solrClient.query(CORE_NAME, solrQuery);
        return mapToSearchResult(response);
    }

    @Override
    public FacetedSearchResult advancedSearch(String query, Map<String, String> fieldFilters,
                                               int limit, String... facetFields)
            throws SolrServerException, IOException {
        SolrQuery solrQuery = buildBaseQuery(query, limit, facetFields);

        if (fieldFilters != null && !fieldFilters.isEmpty()) {
            for (Map.Entry<String, String> filter : fieldFilters.entrySet()) {
                solrQuery.addFilterQuery(filter.getKey() + ":(\"" + filter.getValue() + "\")");
            }
        }

        QueryResponse response = solrClient.query(CORE_NAME, solrQuery);
        return mapToSearchResult(response);
    }

    @Override
    public FacetedSearchResult listDocuments(int limit, String... facetFields)
            throws SolrServerException, IOException {
        return searchWithFacets("*:*", limit, facetFields);
    }


    @Override
    public long getFileCount() throws SolrServerException, IOException {
        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        QueryResponse response = solrClient.query(CORE_NAME, query);
        return response.getResults().getNumFound();
    }

    @Override
    public long getTotalSize() throws SolrServerException, IOException {
        SolrQuery query = new SolrQuery("*:*");
        query.setRows(0);
        query.setGetFieldStatistics("size");
        QueryResponse response = solrClient.query(CORE_NAME, query);
        Object sum = response.getFieldStatsInfo().get("size").getSum();
        return sum != null ? ((Number) sum).longValue() : 0L;
    }


    @Override
    public String generateId() {
        return UUID.randomUUID().toString();
    }


    private SolrQuery buildBaseQuery(String query, int limit, String[] facetFields) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery((query == null || query.trim().isEmpty()) ? "*:*" : query);
        solrQuery.setRows(limit);
        solrQuery.set("defType", "edismax");
        solrQuery.set("qf", "name title content description author");
        solrQuery.set("fl", "*,score");

        solrQuery.setFacet(true);
        String[] fields = (facetFields != null && facetFields.length > 0)
                ? facetFields
                : DEFAULT_FACET_FIELDS;
        solrQuery.addFacetField(fields);
        solrQuery.setFacetLimit(FACET_LIMIT);
        solrQuery.setFacetMinCount(FACET_MIN_COUNT);
        solrQuery.set("facet.sort", "count");

        return solrQuery;
    }

    private FacetedSearchResult mapToSearchResult(QueryResponse response) {
        SolrDocumentList results = response.getResults();

        List<FileDocument> documents = results.stream()
            .map(doc -> FileDocument.builder()
                .id(asString(doc.getFieldValue("id")))
                .name(asString(doc.getFieldValue("name")))
                .title(asString(doc.getFieldValue("title")))
                .author(asString(doc.getFieldValue("author")))
                .content(asString(doc.getFieldValue("content")))
                .fileType(asString(doc.getFieldValue("filetype")))
                .size(asLong(doc.getFieldValue("size")))
                .uploadDate(asDate(doc.getFieldValue("upload_date")))
                .createdDate(asDate(doc.getFieldValue("created_date")))
                .description(asString(doc.getFieldValue("description")))
                .score(asFloat(doc.getFieldValue("score")))
                .build())
            .collect(Collectors.toList());

        Map<String, Map<String, Long>> manualCounts = new HashMap<>();
        manualCounts.put("titles", new HashMap<>());
        manualCounts.put("authors", new HashMap<>());
        manualCounts.put("file_types", new HashMap<>());
        manualCounts.put("created_dates", new HashMap<>());

        for (FileDocument doc : documents) {
            incrementCount(manualCounts.get("titles"), doc.getTitle());
            incrementCount(manualCounts.get("authors"), doc.getAuthor());
            incrementCount(manualCounts.get("file_types"), doc.getFileType());

            if (doc.getCreatedDate() != null) {
                incrementCount(manualCounts.get("created_dates"), doc.getCreatedDate().toString());
            }
        }

        Map<String, List<FacetValue>> facets = new LinkedHashMap<>();
        manualCounts.forEach((key, valueMap) -> {
            List<FacetValue> values = valueMap.entrySet().stream()
                .map(entry -> FacetValue.builder()
                    .value(entry.getKey())
                    .count(entry.getValue())
                    .build())
                .sorted(Comparator.comparingLong(FacetValue::getCount).reversed())
                .collect(Collectors.toList());
            facets.put(key, values);
        });

        return FacetedSearchResult.builder()
                .documents(documents)
                .totalCount(results.getNumFound())
                .facets(facets)
                .queryTime((float) response.getQTime() / 1000f)
                .build();
    }

    private void incrementCount(Map<String, Long> countMap, String value) {
        if (value != null && !value.isBlank()) {
            countMap.put(value, countMap.getOrDefault(value, 0L) + 1);
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Date asDate(Object value) {
        return value instanceof Date date ? date : null;
    }

    private Float asFloat(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.floatValue();
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}