package solr.solr.utils;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import solr.solr.data.DocumentMetadata;
import solr.solr.utils.Interface.IFileProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.Date;

@Component
public class FileProcessor implements IFileProcessor {

    private static final Tika tika = new Tika();
    private static final int MAX_CONTENT_LENGTH = 100_000; // 100k chars cap

    // ─── MIME Detection ────────────────────────────────────────────────────────

    @Override
    public String detectMimeType(MultipartFile file) throws IOException {
        // Tika reads magic bytes — never trusts the browser Content-Type
        try (InputStream is = file.getInputStream()) {
            return tika.detect(is, file.getOriginalFilename());
        }
    }

    // ─── Metadata Extraction (routes by MIME) ──────────────────────────────────

    @Override
    public DocumentMetadata extractMetadata(MultipartFile file) throws IOException {
        String mimeType = detectMimeType(file);

        return switch (mimeType) {
            case "application/pdf"                                                   -> extractPdf(file);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/msword"                                                -> extractWord(file);
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel"                                          -> extractExcel(file);
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.ms-powerpoint"                                     -> extractPowerPoint(file);
            case "text/plain", "text/csv", "text/html",
                 "application/json", "application/xml", "text/xml"                  -> extractPlainText(file);
            case "image/jpeg", "image/png", "image/gif",
                 "image/webp", "image/tiff"                                          -> extractImage(file);
            default                                                                  -> extractGeneric(file, mimeType);
        };
    }

    // ─── Per-type extractors ────────────────────────────────────────────────────

    /** PDF: full text + author + creation date via Tika AutoDetectParser */
    private DocumentMetadata extractPdf(MultipartFile file) throws IOException {
        return tikaExtract(file, "PDF");
    }

    /** DOCX / DOC */
    private DocumentMetadata extractWord(MultipartFile file) throws IOException {
        return tikaExtract(file, "Word");
    }

    /** XLSX / XLS — Tika flattens cells to text rows */
    private DocumentMetadata extractExcel(MultipartFile file) throws IOException {
        return tikaExtract(file, "Excel");
    }

    /** PPTX / PPT — Tika extracts slide text */
    private DocumentMetadata extractPowerPoint(MultipartFile file) throws IOException {
        return tikaExtract(file, "PowerPoint");
    }

    /** Plain text, CSV, JSON, XML, HTML */
    private DocumentMetadata extractPlainText(MultipartFile file) throws IOException {
        String raw = new String(file.getBytes());
        String content = raw.length() > MAX_CONTENT_LENGTH
                ? raw.substring(0, MAX_CONTENT_LENGTH)
                : raw;

        return DocumentMetadata.builder()
                .title(file.getOriginalFilename())
                .content(content)
                .build();
    }

    /** Images: no real text — store filename as title, empty content */
    private DocumentMetadata extractImage(MultipartFile file) throws IOException {
        // Hook here for an OCR library (e.g. Tesseract) if you add it later
        return DocumentMetadata.builder()
                .title(file.getOriginalFilename())
                .content("") // or: ocrExtract(file)
                .build();
    }

    private DocumentMetadata extractGeneric(MultipartFile file, String mimeType) {
        try {
            return tikaExtract(file, mimeType);
        } catch (Exception e) {
            System.err.println("Generic extraction failed for " + mimeType + ": " + e.getMessage());
            return DocumentMetadata.builder()
                    .title(file.getOriginalFilename())
                    .content("")
                    .build();
        }
    }


    private DocumentMetadata tikaExtract(MultipartFile file, String label) throws IOException {
        BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_LENGTH);
        Metadata tikaMetadata = new Metadata();
        ParseContext context = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();

        try (InputStream is = file.getInputStream()) {
            parser.parse(is, handler, tikaMetadata, context);
        } catch (Exception e) {
            throw new IOException("Tika extraction failed for " + label + ": " + e.getMessage(), e);
        }

        String title  = firstNonNull(
                tikaMetadata.get(TikaCoreProperties.TITLE),
                file.getOriginalFilename()
        );
        String author = firstNonNull(
                tikaMetadata.get(TikaCoreProperties.CREATOR),
                tikaMetadata.get("Author")
        );

        Date createdDate = null;
        String dateStr = tikaMetadata.get(TikaCoreProperties.CREATED);
        if (dateStr != null) {
            try {
                createdDate = Date.from(OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME).toInstant());
            } catch (Exception ignored) { /* malformed date — leave null */ }
        }

        String content = handler.toString().trim();

        return DocumentMetadata.builder()
                .title(title)
                .author(author)
                .createdDate(createdDate)
                .content(content)
                .build();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}