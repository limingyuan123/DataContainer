package njgis.opengms.datacontainer.dao;

import njgis.opengms.datacontainer.entity.DataListCom;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * @Author mingyuan
 * @Date 2020.07.08 14:08
 */
public interface DataListComDao extends MongoRepository<DataListCom, String> {
    DataListCom findFirstByOid(String oid);
}
