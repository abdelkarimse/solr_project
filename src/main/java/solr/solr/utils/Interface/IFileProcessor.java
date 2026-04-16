package solr.solr.utils.Interface;

import org.springframework.web.multipart.MultipartFile;
import solr.solr.data.DocumentMetadata;



import java.io.IOException;

public interface IFileProcessor {
    String detectMimeType(MultipartFile file) throws IOException;
    DocumentMetadata extractMetadata(MultipartFile file) throws IOException;
}