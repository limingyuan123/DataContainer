package njgis.opengms.datacontainer.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * @Author mingyuan
 * @Date 2021.06.01 19:48
 */
@Document
@Data
public class TimeStamp {
    @Id
    String oid;
    Date time;
}
