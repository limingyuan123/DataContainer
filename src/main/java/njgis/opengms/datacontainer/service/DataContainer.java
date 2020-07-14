package njgis.opengms.datacontainer.service;

import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.BulkDataLinkDao;
import njgis.opengms.datacontainer.dao.DataListComDao;
import njgis.opengms.datacontainer.dao.DataListDao;
import njgis.opengms.datacontainer.dao.ReferenceZeroTimeDao;
import njgis.opengms.datacontainer.entity.BulkDataLink;
import njgis.opengms.datacontainer.entity.DataList;
import njgis.opengms.datacontainer.entity.DataListCom;
import njgis.opengms.datacontainer.entity.ReferenceZeroTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @Author mingyuan
 * @Date 2020.06.13 16:48
 */
@Service
@Slf4j
@Component
public class DataContainer {
    @Autowired
    DataListDao dataListDao;

    @Autowired
    DataListComDao dataListComDao;

    @Autowired
    BulkDataLinkDao bulkDataLinkDao;

    @Autowired
    ReferenceZeroTimeDao referenceZeroTimeDao;

    @Value("${resourcePath}")
    private String resourcePath;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public boolean uploadOGMSMulti(BulkDataLink bulkDataLink,String ogmsPath, String uuid, MultipartFile[] files,Boolean configExist) throws IOException {
        BufferedInputStream bis = null;
        ZipOutputStream zos = null;
        InputStream inputStream = null;
        DataList dataList = new DataList();
        try {
            File filePath = new File(ogmsPath);
            if (!filePath.exists()){
                filePath.mkdirs();
            }
            String fileName = uuid + ".zip";
            File zip = new File(filePath, fileName);
            zip.createNewFile();
            zos = new ZipOutputStream(new FileOutputStream(zip));
            byte[] bufs = new byte[1024 * 10];


            //如果为多个，则将上传的文件进行压缩，之后进行上传，配置文件不压缩
            for (int i = 0; i < files.length; i++) {
                //有些上传文件无后缀，筛选无后缀文件
                boolean isMatchSuffix = files[i].getOriginalFilename().contains(".");
                if (isMatchSuffix){
                    //对文文件的全名进行截取然后在后缀名进行删选。
                    int begin = files[i].getOriginalFilename().indexOf(".");
                    int last = files[i].getOriginalFilename().length();
                    //获得文件后缀名
                    String a = files[i].getOriginalFilename().substring(begin, last);
                    //如文件为配置文件，则不压缩
                    if (a.endsWith(".udxcfg")){
                        break;
                    }
                }

                inputStream = files[i].getInputStream();
                String streamFileName = files[i].getOriginalFilename();

                ZipEntry zipEntry = new ZipEntry(streamFileName);
                zos.putNextEntry(zipEntry);

                bis = new BufferedInputStream(inputStream, 1024 * 10);
                int read = 0;
                while ((read = bis.read(bufs, 0, 1024 * 10)) != -1) {
                    zos.write(bufs, 0, read);
                }
            }
            zos.flush();
            zos.close();

            inputStream = new FileInputStream(zip);
        }catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            // 关闭流
            try {
                if (null != bis)
                    bis.close();
                if (null != zos)
                    zos.close();
                if (inputStream!=null){
                    inputStream.close();
                }

            } catch (IOException e) {
                log.error("InputStream or OutputStream close error : {}", e);
            }
        }
        //在该文件夹内，仍存储未压缩文件
        uploadOGMSData(bulkDataLink,files,configExist);
        return true;
    }
    //将每个文件分开存储并存储oid
    public boolean uploadOGMSData(BulkDataLink bulkDataLink, MultipartFile[] files,Boolean configExist) throws IOException {
        List<String> dataOids = new LinkedList<>();
        int fileLength;
        if (configExist){
            fileLength = files.length-1;//有配置文件，但不存储配置文件
        }else {
            fileLength = files.length;
        }
        //不存储config文件
        for (int i=0;i<fileLength;i++) {
            DataListCom dataListCom = new DataListCom();
            String oid = UUID.randomUUID().toString();
            String ogmsPath = resourcePath + "/" + oid;
            MultipartFile file = files[i];
            String md5;
            //先进行md5值匹配，已存在则不再上传文件，md5+1，不存在则上传文件，md5初始化为1
            md5 = DigestUtils.md5DigestAsHex(file.getInputStream());//生成md5值
            String id;
            boolean isMatch = false;
            List<DataListCom> dataListComs = dataListComDao.findAll();
            for (DataListCom dataListCom1 : dataListComs){
                if (md5.equals(dataListCom1.getMd5())){
                    isMatch = true;
                    id = dataListCom1.getOid();
                    referenceCountPlusPlus(id);
                    dataOids.add(dataListCom1.getOid());
                    break;
                }
            }
            if (isMatch){
                continue;
            }

            File localFile = new File(ogmsPath);
            //先创建目录
            if (!localFile.exists()) {
                localFile.mkdirs();
            }
            String originalFilename = file.getOriginalFilename();
            String path = ogmsPath + "/" + originalFilename;

            log.info("createLocalFile path = {}", path);

            localFile = new File(path);
            FileOutputStream fos = null;
            InputStream in = null;
            try {
                if (localFile.exists()) {
                    //如果文件存在删除文件
                    boolean delete = localFile.delete();
                    if (delete == false) {
                        log.error("Delete exist file \"{}\" failed!!!", path, new Exception("Delete exist file \"" + path + "\" failed!!!"));
                    }
                }
                //创建文件
                if (!localFile.exists()) {
                    //如果文件不存在，则创建新的文件
                    localFile.createNewFile();
                    log.info("Create file successfully,the file is {}", path);
                }

                //创建文件成功后，写入内容到文件里
                fos = new FileOutputStream(localFile);
                in = file.getInputStream();

                byte[] bytes = new byte[1024];
                int len = -1;
                while ((len = in.read(bytes)) != -1) {
                    fos.write(bytes, 0, len);
                }
                fos.flush();
                log.info("Reading uploaded file and buffering to local successfully!");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                }catch (IOException e) {
                    log.error("InputStream or OutputStream close error : {}", e);
                    return false;
                }
            }

            dataListCom.setOid(oid);
            dataListCom.setPath(resourcePath + "/" + oid);
            dataListCom.setFileName(files[i].getOriginalFilename());
            dataListCom.setMd5(md5);
            dataListCom.setReferenceCount(1);//引用计数初始化为1
            dataListComDao.save(dataListCom);
            dataOids.add(oid);
        }
        bulkDataLink.setDataOids(dataOids);
        return true;
    }

    //文件下载
    public boolean downLoad(String oid, HttpServletResponse response) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
        if (bulkDataLink == null){
            //两种情况，一种情况是oid输入错误，另一种情况是输入的是dataListCom的oid
            DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
            if (dataListCom == null){
                downLoadLog = false;
            }else {
                //下载单文件
                String fileSingle = dataListCom.getPath() + "/"+dataListCom.getFileName();
                File file = new File(fileSingle);
                String fileName = dataListCom.getFileName();
                if (file.exists()){
                    downLoadLog = downLoadFile(response, file, fileName);
                }
            }
        }else {
            //下载批量文件包（一个文件上传被压缩的也算）。判断文件个数,单文件不压缩，多文件压缩
            String downLoadPath = bulkDataLink.getPath();
            if (bulkDataLink.getDataOids().size() == 1) {
                String fileOid = bulkDataLink.getDataOids().get(0);
                DataListCom dataListCom = dataListComDao.findFirstByOid(fileOid);
                String fileSingle = dataListCom.getPath() + "/" + dataListCom.getFileName();
                File file = new File(fileSingle);
                String suffix = fileSingle.substring(fileSingle.lastIndexOf(".") + 1);
                String fileName = bulkDataLink.getName() + "." + suffix;//下载时的文件名称,门户，下载的名字为前端定义的，不是真名
//            String fileName = dataListCom.getFileName();//下载时的文件名称,为文件真名，需要时取消注释
                if (file.exists()) {
                    downLoadLog = downLoadFile(response, file, fileName);
                }
            } else {
                String fileZip = bulkDataLink.getZipOid() + ".zip";
                String fileName = bulkDataLink.getName() + ".zip";
                File file = new File(downLoadPath + "/" + fileZip);
                if (file.exists()) {
                    downLoadLog = downLoadFile(response, file, fileName);
                }
            }
        }
        return downLoadLog;
    }

    public boolean downLoadFile(HttpServletResponse response, File file, String fileName) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        response.setContentType("application/force-download");
        response.addHeader("Content-Disposition", "attachment;fileName=" + new String(fileName.getBytes(StandardCharsets.UTF_8),"ISO8859-1"));
        byte[] buffer = new byte[1024];
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            OutputStream outputStream = response.getOutputStream();
            int i = bis.read(buffer);
            while (i != -1) {
                outputStream.write(buffer, 0, i);
                i = bis.read(buffer);
            }
            downLoadLog = true;
            //return "下载成功";
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return downLoadLog;
    }

    //文件批量下载
    public JsonResult downLoadBulkFile(HttpServletResponse response, File[] files) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        JsonResult jsonResult = new JsonResult();

        byte[] buffer = new byte[1024*10];

        ZipOutputStream zos = null;
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        InputStream inputStream = null;

        String zipUuid = UUID.randomUUID().toString();
        String zipPath = resourcePath + "/" + zipUuid;
        String zipFileName = zipUuid + ".zip";

        //首先对files进行压缩存储为一个new zip
        try {
            File fileZipPath = new File(zipPath);
            if (!fileZipPath.exists()){
                fileZipPath.mkdirs();
            }
            File zipBulk = new File(fileZipPath,zipFileName);
            zipBulk.createNewFile();

            zos = new ZipOutputStream(new FileOutputStream(zipBulk));
            for (int i=0;i<files.length;i++){
                fis = new FileInputStream(files[i]);
                ZipEntry zipEntry = new ZipEntry(files[i].getName());
                zos.putNextEntry(zipEntry);
                bis = new BufferedInputStream(fis,1024*10);
                int read = 0;
                while((read = bis.read(buffer, 0, 1024 * 10)) != -1)
                {
                    zos.write(buffer, 0, read);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (bis != null){
                    bis.close();
                }
                if (zos != null){
                    zos.close();
                }
                if (fis != null){
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String fileName = "BulkFile.zip";
        response.setContentType("application/force-download");
        response.addHeader("Content-Disposition", "attachment;fileName=" + new String(fileName.getBytes(StandardCharsets.UTF_8),"ISO8859-1"));

        try {
            OutputStream outputStream = response.getOutputStream();
            File file = new File(zipPath + "/" + zipFileName);
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            int ops = bis.read(buffer);
            while (ops != -1) {
                outputStream.write(buffer, 0, ops);
                ops = bis.read(buffer);
            }
            downLoadLog = true;
            //return "下载成功";
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        jsonResult.setData(zipPath);
        jsonResult.setCode(0);
        return jsonResult;
    }

    //文件删除操作
    public boolean delete(String oid){
        boolean delLog = false;

        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
        //删除文件夹或文件,如删除批量上传的文件，则所有文件的文件夹也删除
//        for (int i=0;i<bulkDataLink.getDataOids().size();i++){
//            DataListCom dataListCom = dataListComDao.findFirstByOid(bulkDataLink.getDataOids().get(i));
//            String delPath = dataListCom.getPath();
//            delLog = deleteFolder(delPath);
//        }
        //删除dataListCom中文件只引用系数减一
        for (int i=0;i<bulkDataLink.getDataOids().size();i++){
            DataListCom dataListCom = dataListComDao.findFirstByOid(bulkDataLink.getDataOids().get(i));
            if (dataListCom.getReferenceCount()>0) {
                referenceCountMinusMinus(dataListCom.getOid());
                DataListCom dataListCom1 = dataListComDao.findFirstByOid(bulkDataLink.getDataOids().get(i));
                if (dataListCom1.getReferenceCount() == 0){
                    //记录该文件删除时间
                    Date deleteTime = new Date();
                    ReferenceZeroTime referenceZeroTime = new ReferenceZeroTime();
                    referenceZeroTime.setDataListComOid(dataListCom1.getOid());
                    referenceZeroTime.setReferenceZeroTime(deleteTime);
                    referenceZeroTimeDao.save(referenceZeroTime);
                }
            }else if (dataListCom.getReferenceCount()<=0){
                delLog = false;
                return delLog;//考虑到引用系数已经为0还要删除的情况
            }
        }
        //dataOids文件夹删除后删除zip文件夹
        String delPath = bulkDataLink.getPath();
        delLog = deleteFolder(delPath);

        if (delLog){
            //删除对应的数据库内容
            if (bulkDataLink!=null) {
//                for (int i=0;i<bulkDataLink.getDataOids().size();i++){
//                    DataListCom dataListCom = dataListComDao.findFirstByOid(bulkDataLink.getDataOids().get(i));
//                    dataListComDao.delete(dataListCom);
//                }
                bulkDataLinkDao.delete(bulkDataLink);
            }
        }

        return delLog;
    }

    //根据路径删除指定的目录或文件，无论存在与否
    public boolean deleteFolder(String sPath) {
        boolean delLog = false;

        File file = new File(sPath);
        // 判断目录或文件是否存在
        if (!file.exists()) {
            return delLog;
        } else {
            // 判断是否为文件
            if (file.isFile()) {  // 为文件时调用删除文件方法
                return deleteFile(sPath);
            } else {  // 为目录时调用删除目录方法
                return deleteDirectory(sPath);
            }
        }
    }

     //删除单个文件
    public boolean deleteFile(String sPath) {
        boolean delLog;
        delLog = false;
        File file = new File(sPath);
        // 路径为文件且不为空则进行删除
        if (file.isFile() && file.exists()) {
            file.delete();
            delLog = true;
        }
        return delLog;
    }

    //删除目录（文件夹）以及目录下的文件
    public boolean deleteDirectory(String sPath) {
        boolean delLog;
        //如果sPath不以文件分隔符结尾，自动添加文件分隔符
        if (!sPath.endsWith(File.separator)) {
            sPath = sPath + File.separator;
        }
        File dirFile = new File(sPath);
        //如果dir对应的文件不存在，或者不是一个目录，则退出
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            return false;
        }
        delLog = true;
        //删除文件夹下的所有文件(包括子目录)
        File[] files = dirFile.listFiles();
        for (int i = 0; i < files.length; i++) {
            //删除子文件
            if (files[i].isFile()) {
                delLog = deleteFile(files[i].getAbsolutePath());
                if (!delLog) break;
            } //删除子目录
            else {
                delLog = deleteDirectory(files[i].getAbsolutePath());
                if (!delLog) break;
            }
        }
        if (!delLog) return false;
        //删除当前目录
        if (dirFile.delete()) {
            return true;
        } else {
            return false;
        }
    }

    //引用计数++
    public void referenceCountPlusPlus(String oid){
        DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
        int count = dataListCom.getReferenceCount() + 1;
        dataListCom.setReferenceCount(count);
        dataListComDao.save(dataListCom);
    }

    //引用计数--
    public void referenceCountMinusMinus(String oid){
        DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
        int count = dataListCom.getReferenceCount() - 1;
        dataListCom.setReferenceCount(count);
        dataListComDao.save(dataListCom);
    }

    //对md5值为0且为0时间超30天的文件进行删除
    //先测试5秒钟的
    @Scheduled(cron = "*/5 * * * * ?")
    //部署时解开
//    @Scheduled(cron = "0 0 23 * * ?")
    private void process(){
        timeOutDelete();
    }
    private void timeOutDelete(){
        boolean delLog = false;
        Date currentTime = new Date();
        List<ReferenceZeroTime> referenceZeroTimes = referenceZeroTimeDao.findAll();
        if (referenceZeroTimes!=null) {
            for (ReferenceZeroTime referenceZeroTime : referenceZeroTimes) {
                Date deleteTime = referenceZeroTime.getReferenceZeroTime();
                long diff = currentTime.getTime() - deleteTime.getTime();
                //部署时解开
//            if (diff/(24*60*60*1000)>30){
                if (diff / 1000 % 60 > 10) {
                    //先测试10秒钟的
                    //如果删除时间大于30天，则此数据删除
                    delLog = deleteDataListCom(referenceZeroTime.getDataListComOid());
                }
            }
        }
//        if (!delLog){
//            logger.error("删除失败，或无文件");
//        }
    }
    public Boolean deleteDataListCom(String oid){
        boolean delLog = false;

        DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
        //删除文件
        String delPath = dataListCom.getPath();
        delLog = deleteFolder(delPath);
        //删除数据库内容
        dataListComDao.delete(dataListCom);
        //删除时间记录表
        ReferenceZeroTime referenceZeroTime = referenceZeroTimeDao.findFirstByDataListComOid(oid);
        referenceZeroTimeDao.delete(referenceZeroTime);

        return delLog;
    }
}