package njgis.opengms.datacontainer.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @Author mingyuan
 * @Date 2020.07.08 11:08
 */
@Document
@Data
public class BulkDataLink {
    @Id
    String zipOid;
    List<String> dataOids;//链接DataListCom的数据
    String name;//批量上传包名，门户
    String origination;
    String serverNode;
    String uid;
    Date date;
    String type;
    String dataTemplate;//存储type为schema的udx串
    String dataTemplateId;//id去掉
    String path;
    Boolean configFile;
    Boolean Cache;//超过七天无下载则直接删除该数据（不是表），再下载则根据表找到数据列表进行打包
    List<DataListCom> dataListComs;
}
