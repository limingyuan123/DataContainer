package njgis.opengms.datacontainer.dao;

import njgis.opengms.datacontainer.entity.BulkDataLink;
import njgis.opengms.datacontainer.entity.BulkDataLink2;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * @Author mingyuan
 * @Date 2020.07.08 15:24
 */
public interface BulkDataLinkDao extends MongoRepository<BulkDataLink,String> {
    BulkDataLink findFirstByZipOid(String zipOid);

    List<BulkDataLink> findAllByOrigination(String origination);

    List<BulkDataLink> findAllByDataOids(String oid);

    BulkDataLink findByDataOids(String oid);

    void insert(BulkDataLink2 bulkDataLink2);
}
