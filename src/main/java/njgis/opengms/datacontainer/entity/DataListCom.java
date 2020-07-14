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
    String fileName;
    String path;
    //md5与referenceCount只在DataListCom中有，只是计文件的，BulkDataLink只是链接批量文件所用
    String md5;
    int referenceCount;
}
