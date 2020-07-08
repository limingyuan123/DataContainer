package njgis.opengms.datacontainer.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
/**
 * @Author mingyuan
 * @Date 2020.07.08 10:44
 */
@Document
@Data
public class DataListCom {
    @Id
    String oid;
    String path;
}
