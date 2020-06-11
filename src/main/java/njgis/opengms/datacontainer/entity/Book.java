package njgis.opengms.datacontainer.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
public class Book {
    private Integer id;
    private String name;
    private String author;
}
