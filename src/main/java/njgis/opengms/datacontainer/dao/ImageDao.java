package njgis.opengms.datacontainer.dao;

import njgis.opengms.datacontainer.entity.Image;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * @Auther mingyuan
 * @Data 2020.06.11 20:22
 */
public interface ImageDao extends MongoRepository<Image,String> {
    Image findByOid(String oid);

}
