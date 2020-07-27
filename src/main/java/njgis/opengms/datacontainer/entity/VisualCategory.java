package njgis.opengms.datacontainer.entity;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @Author mingyuan
 * @Date 2020.07.17 16:05
 */
@Document
@Data
public class VisualCategory {
    String oid;
    String category;
}
