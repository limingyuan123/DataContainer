package njgis.opengms.datacontainer.dao;

import njgis.opengms.datacontainer.entity.BulkDataLink;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * @Author mingyuan
 * @Date 2020.07.08 15:24
 */
public interface BulkDataLinkDao extends MongoRepository<BulkDataLink,String> {
    BulkDataLink findFirstByZipOid(String zipOid);
}
