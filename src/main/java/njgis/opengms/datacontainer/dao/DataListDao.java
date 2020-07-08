package njgis.opengms.datacontainer.dao;

import njgis.opengms.datacontainer.entity.DataList;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * @Author mingyuan
 * @Date 2020.06.14 16:10
 */
public interface DataListDao extends MongoRepository<DataList, String> {
    DataList findByOid(String oid);
    DataList findFirstByUid(String uid);
}
