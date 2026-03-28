package solr.solr.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMetadata {
    private String title;
    private String author;
    private String subject;
    private String keywords;
    private Date createdDate;
    private Date modifiedDate;
    private String creator;
    private String producer;
    private String content;
    @Builder.Default
    private Map<String, String> customMetadata = new HashMap<>();
}
