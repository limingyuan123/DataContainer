package njgis.opengms.datacontainer.thread;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.dao.DataListComDao;
import njgis.opengms.datacontainer.dao.DataListDao;
import njgis.opengms.datacontainer.entity.BulkDataLink;
import njgis.opengms.datacontainer.entity.DataList;
import njgis.opengms.datacontainer.entity.DataListCom;
import njgis.opengms.datacontainer.service.DataContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @Author mingyuan
 * @Date 2020.08.27 20:53
 */
@Slf4j
public class UploadThread implements Runnable {
    private int thread;
    private MultipartFile file;
    public List<String> dataOids;
    private String resourcePath;
    private DataListComDao dataListComDao;
    private BulkDataLink bulkDataLink;


    public UploadThread(MultipartFile file, int thread, List<String> dataOids, String resourcePath,
                        DataListComDao dataListComDao,BulkDataLink bulkDataLink) {
        this.thread = thread;
        this.file = file;
        this.dataOids = dataOids;
        this.resourcePath = resourcePath;
        this.dataListComDao = dataListComDao;
        this.bulkDataLink = bulkDataLink;
    }

    @SneakyThrows
    @Override
    public void run() {
        //存储文件的oid
        String oid = UUID.randomUUID().toString();

        List<String> dataOids;
        dataOids = bulkDataLink.getDataOids();

        String md5;
        //先进行md5值匹配，已存在则不再上传文件，md5+1，不存在则上传文件，md5初始化为1
        md5 = DigestUtils.md5DigestAsHex(file.getInputStream());//生成md5值
        String id;
        boolean isMatch = false;
        List<DataListCom> dataListComs = dataListComDao.findAll();
        for (DataListCom dataListCom1 : dataListComs) {
            if (md5.equals(dataListCom1.getMd5())) {
                isMatch = true;
                id = dataListCom1.getOid();
//                DataContainer dataContainer = new DataContainer();
                referenceCountPlusPlus(id);
                if (dataOids!=null) {
                    dataOids.add(dataListCom1.getOid());
                }else {
                    dataOids = new ArrayList<>();
                    dataOids.add(dataListCom1.getOid());
                }
                break;
            }
        }
        if (!isMatch){
            //上传文件至文件夹下
            DataListCom dataListCom = new DataListCom();
            String ogmsPath = resourcePath + "/" + oid;
            File localFile = new File(ogmsPath);
            if (!localFile.exists()) {
                localFile.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String path = ogmsPath + "/" + originalFilename;

            localFile = new File(path);
            FileOutputStream fos = null;
            InputStream in = null;
            try {
                if (localFile.exists()) {
                    boolean delete = localFile.delete();
                    if (!delete) {
                        log.error("Delete exist file failed");
                    }
                } else {
                    localFile.createNewFile();
                }
                fos = new FileOutputStream(localFile);
                in = file.getInputStream();
                byte[] bytes = new byte[1024];
                int len = -1;
                while ((len = in.read(bytes)) != -1) {
                    fos.write(bytes, 0, len);
                }
                fos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            dataListCom.setOid(oid);
            dataListCom.setPath(resourcePath + "/" + oid);
            dataListCom.setFileName(file.getOriginalFilename());
//        dataListCom.setMd5(md5);
            dataListCom.setReferenceCount(1);//引用计数初始化为1
            dataListComDao.save(dataListCom);

            if (dataOids != null) {
                dataOids.add(oid);
            } else {
                dataOids = new ArrayList<>();
                dataOids.add(oid);
            }
        }
        bulkDataLink.setDataOids(dataOids);
        log.info("Thread " + thread + " run finish!");
    }

    public void referenceCountPlusPlus(String oid){
        DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
        int count = dataListCom.getReferenceCount() + 1;
        dataListCom.setReferenceCount(count);
        dataListComDao.save(dataListCom);
    }
}
