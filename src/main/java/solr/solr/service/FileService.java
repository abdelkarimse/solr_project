package solr.solr.service;

import lombok.AllArgsConstructor;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import solr.solr.data.DocumentMetadata;
import solr.solr.data.FileDocument;
import solr.solr.data.FacetedSearchResult;
import solr.solr.repositories.Interface.ISolrRepository;
import solr.solr.service.Interface.IFileService;
import solr.solr.utils.Interface.IFileProcessor;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

@Service
@AllArgsConstructor
public class FileService implements IFileService {

    private ISolrRepository solrRepository;
    private IFileProcessor fileProcessor;
    private FileStorageService fileStorageService;
    
    public FileDocument indexFile(MultipartFile file) throws IOException, SolrServerException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String id = solrRepository.generateId();
        
        String storagePath = fileStorageService.saveFile(file);
        
        DocumentMetadata metadata = fileProcessor.extractMetadata(file);
        String content = metadata.getContent() != null ? metadata.getContent() : "";
        
        String fileType = fileProcessor.detectMimeType(file);
        
        String title = metadata.getTitle() != null ? metadata.getTitle() : file.getOriginalFilename();
        
        FileDocument fileDocument = FileDocument.builder()
                .id(id)
                .name(file.getOriginalFilename())
                .title(title)
                .author(metadata.getAuthor())
                .content(content)
                .fileType(fileType)
                .size(file.getSize())
                .uploadDate(new Date())
                .createdDate(metadata.getCreatedDate())
                .description("Indexed file: " + file.getOriginalFilename())
                .build();
        solrRepository.indexDocument(fileDocument);

        return fileDocument;
    }

    @Override
    public FacetedSearchResult searchWithFacets(String query, int limit, String... facetFields) 
            throws SolrServerException, IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }
        return solrRepository.searchWithFacets(query.trim(), limit, facetFields);
    }

    @Override
    public FacetedSearchResult advancedSearch(String query, Map<String, String> fieldFilters, int limit, String... facetFields) 
            throws SolrServerException, IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }
        return solrRepository.advancedSearch(query.trim(), fieldFilters, limit, facetFields);
    }
    
    public void deleteFile(String id) throws SolrServerException, IOException {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be empty");
        }

        solrRepository.deleteDocument(id);
    }
}
