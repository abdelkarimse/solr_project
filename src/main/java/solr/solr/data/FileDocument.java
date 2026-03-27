package solr.solr.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.solr.client.solrj.beans.Field;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDocument {

    @Field("id")
    private String id;

    @Field("name")
    private String name;

    @Field("title")
    private String title;

    @Field("author")
    private String author;

    @Field("content")
    private String content;

    @Field("filetype")
    private String fileType;

    @Field("size")
    private Long size;

    @Field("upload_date")
    private Date uploadDate;

    @Field("created_date")
    private Date createdDate;

    @Field("description")
    private String description;

    private Float score;

    @Field("keywords")
    private List<String> keywords;
}
