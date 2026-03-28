package solr.solr.utils;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import solr.solr.data.DocumentMetadata;
import solr.solr.utils.Interface.IFileProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class FileProcessor implements IFileProcessor {

    private final Tika tika;

    public FileProcessor() {
        this.tika = new Tika();
    }
    
    public String extractContent(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.parseToString(inputStream);
        } catch (TikaException | IOException e) {
            System.err.println("Error extracting content from file: " + file.getOriginalFilename() + " - " + e.getMessage());
            return "";
        }
    }
    
    public String detectMimeType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream);
        } catch (IOException e) {
            return file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        }
    }
    
    public String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    @Override
    public DocumentMetadata extractMetadata(MultipartFile file) {
        DocumentMetadata metadata = DocumentMetadata.builder().build();
        
        try {
            byte[] fileBytes = file.getBytes();
            
            Metadata tikaMetadata = new Metadata();
            Parser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();
            
            
            java.io.ByteArrayInputStream metadataStream = new java.io.ByteArrayInputStream(fileBytes);
            parser.parse(metadataStream, new DefaultHandler(), tikaMetadata, context);
            
            metadata.setTitle(tikaMetadata.get("title"));
            metadata.setAuthor(tikaMetadata.get("Author"));
            metadata.setSubject(tikaMetadata.get("subject"));
            metadata.setKeywords(tikaMetadata.get("keywords"));
            metadata.setCreator(tikaMetadata.get("creator"));
            metadata.setProducer(tikaMetadata.get("pdf:Producer"));
            
            String createdStr = tikaMetadata.get("dcterms:created");
            if (createdStr == null || createdStr.isEmpty()) {
                createdStr = tikaMetadata.get("Creation-Date");
            }
            if (createdStr != null && !createdStr.isEmpty()) {
                try {
                    metadata.setCreatedDate(parseDateString(createdStr));
                } catch (ParseException e) {
                    System.err.println("Error parsing creation date: " + e.getMessage());
                }
            }
            
            String modifiedStr = tikaMetadata.get("dcterms:modified");
            if (modifiedStr == null || modifiedStr.isEmpty()) {
                modifiedStr = tikaMetadata.get("Last-Modified");
            }
            if (modifiedStr != null && !modifiedStr.isEmpty()) {
                try {
                    metadata.setModifiedDate(parseDateString(modifiedStr));
                } catch (ParseException e) {
                    System.err.println("Error parsing modified date: " + e.getMessage());
                }
            }
            
            java.io.ByteArrayInputStream contentStream = new java.io.ByteArrayInputStream(fileBytes);
            String content = tika.parseToString(contentStream);
            metadata.setContent(content);
            
        } catch (IOException | TikaException | SAXException e) {
            System.err.println("Error extracting metadata from file: " + file.getOriginalFilename() + " - " + e.getMessage());
        }
        
        return metadata;
    }

    private Date parseDateString(String dateStr) throws ParseException {
        SimpleDateFormat[] formats = {
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
            new SimpleDateFormat("yyyy-MM-dd")
        };
        
        for (SimpleDateFormat format : formats) {
            try {
                return format.parse(dateStr);
            } catch (ParseException e) {
            }
        }
        
        throw new ParseException("Unable to parse date: " + dateStr, 0);
    }
}
