package solr.solr.utils.Interface;

import org.springframework.web.multipart.MultipartFile;
import solr.solr.data.DocumentMetadata;


public interface IFileProcessor {

    String extractContent(MultipartFile file);

    String detectMimeType(MultipartFile file);

    String getFileExtension(String filename);

    DocumentMetadata extractMetadata(MultipartFile file);
}
