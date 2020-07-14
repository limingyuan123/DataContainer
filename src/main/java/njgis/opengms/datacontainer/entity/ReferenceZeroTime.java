package njgis.opengms.datacontainer.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * @Author mingyuan
 * @Date 2020.07.09 18:35
 */
@Document
@Data
public class ReferenceZeroTime {
    @Id
    String id;
    String dataListComOid;
    Date referenceZeroTime;
}
