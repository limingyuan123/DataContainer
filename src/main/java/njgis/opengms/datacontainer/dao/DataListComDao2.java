package njgis.opengms.datacontainer.dao;

import njgis.opengms.datacontainer.entity.DataListCom;
import njgis.opengms.datacontainer.entity.DataListCom2;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * @Author mingyuan
 * @Date 2020.12.21 16:39
 */
public interface DataListComDao2 extends MongoRepository<DataListCom2, String> {
    DataListCom2 findFirstByOid(String oid);
}
