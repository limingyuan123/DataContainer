package njgis.opengms.datacontainer.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.*;
import njgis.opengms.datacontainer.entity.BulkDataLink;
import njgis.opengms.datacontainer.entity.DataListCom;
import njgis.opengms.datacontainer.entity.ReferenceZeroTime;
import njgis.opengms.datacontainer.entity.TimeStamp;
import njgis.opengms.datacontainer.enums.ContentTypeEnum;
import njgis.opengms.datacontainer.thread.BPContinueThread;
import njgis.opengms.datacontainer.thread.MergeRunnable;
import njgis.opengms.datacontainer.thread.SplitRunnable;
import njgis.opengms.datacontainer.thread.UploadThread;
import njgis.opengms.datacontainer.utils.FileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import sun.security.timestamp.TSRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static njgis.opengms.datacontainer.utils.FileUtil.leftPad;

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

    @Autowired
    TimeStampDao timeStampDao;

    @Value("${resourcePath}")
    private String resourcePath;

    //计数器，主要用来完成缓存文件删除
    private CountDownLatch latch = null;

    /**
     * 多文件打包上传
     * @param bulkDataLink bulkDataLink
     * @param ogmsPath ogmsPath
     * @param uuid uuid
     * @param files files
     * @param configExist configExist
     * @param apiType apiType
     * @return 上传结果
     * @throws IOException 异常处理
     */
    public boolean uploadOGMSMulti(BulkDataLink bulkDataLink,String ogmsPath, String uuid, MultipartFile[] files,Boolean configExist, String apiType)
            throws IOException {
        BufferedInputStream bis = null;
        ZipOutputStream zos = null;
        InputStream inputStream = null;
//        DataList dataList = new DataList();
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


            //如果为多个，则将上传的文件进行压缩，之后进行上传，配置文件不压缩,但是data接口的所有文件均压缩
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
                    if (a.endsWith(".udxcfg")&&apiType.equals("configData")) {
                        break;
                    }
                }

//                log.info("文件大小" + files[i]);

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

    /**
     * 将每个文件分开存储并存储oid
     * @param bulkDataLink bulkDataLink
     * @param files files
     * @param configExist configExist
     * @return 结果
     * @throws IOException 异常处理
     */
    public boolean uploadOGMSData(BulkDataLink bulkDataLink, MultipartFile[] files,Boolean configExist) throws IOException {
        List<String> dataOids = new LinkedList<>();
        int fileLength;
        if (configExist){
            fileLength = files.length-1;//有配置文件，但不存储配置文件
        }else {
            fileLength = files.length;
        }
        //todo 开始多线程上传文件
//        ExecutorService uploadPool = Executors.newCachedThreadPool();
//        for (int i=0;i<fileLength;i++) {
//            int thread = i+1;//标识是第几个线程
//            MultipartFile file = files[i];
//            uploadPool.execute(new UploadThread(file,thread,dataOids,resourcePath,dataListComDao,bulkDataLink));
//        }

//        不存储config文件
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

    /**
     * 文件下载
     * @param oid oid
     * @param response response
     * @param type type
     * @return 下载结果
     * @throws UnsupportedEncodingException 异常处理
     */
    public boolean downLoad(String oid, HttpServletResponse response, String type) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
        if (bulkDataLink == null){
            //两种情况，一种情况是oid输入错误，另一种情况是输入的是dataListCom的oid
            DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
            if (dataListCom == null) {
                downLoadLog = false;
            }else {
                //下载单文件
                String fileSingle = dataListCom.getPath() + "/"+dataListCom.getFileName();
                File file = new File(fileSingle);
                String fileName = dataListCom.getFileName();
                if (file.exists()){
                    downLoadLog = downLoadFile(response, file, fileName, type);
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
                //下载时的文件名称,门户，下载的名字为前端定义的，不是真名//如果该名称不存在，命名为result
                String fileName = null;
                if(bulkDataLink.getName() == null){
                    fileName = "result." + suffix;
                }else {
                    fileName = bulkDataLink.getName() + "." + suffix;
                }

//            String fileName = dataListCom.getFileName();//下载时的文件名称,为文件真名，需要时取消注释
                if (file.exists()) {
                    downLoadLog = downLoadFile(response, file, fileName,type);
                }
            } else {
                String fileZip = bulkDataLink.getZipOid() + ".zip";
                String fileName = bulkDataLink.getName() + ".zip";
                File file = new File(downLoadPath + "/" + fileZip);
                if (file.exists()) {
                    downLoadLog = downLoadFile(response, file, fileName, type);
                }
            }
        }
        return downLoadLog;
    }

    /**
     * 文件下载函数，参数为需要下载的文件路径下的文件、下载用的文件名以及response
     * @param response response
     * @param file file
     * @param fileName fileName
     * @param type type
     * @return 结果
     * @throws UnsupportedEncodingException 异常处理
     */
    public boolean downLoadFile(HttpServletResponse response, File file, String fileName, String type) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        log.info("文件大小" + file.length());
        if(type!=null){
            String contentType = ContentTypeEnum.getContentTypeByName(type).getText();
            response.setContentType(contentType);
        }else {
            response.setContentType("application/force-download");
            response.addHeader("Content-Disposition", "attachment;fileName=" + new String(fileName.getBytes(StandardCharsets.UTF_8),
                    "ISO8859-1"));
            response.setContentLength((int) file.length());
        }

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

    /**
     * 文件批量下载函数
     * @param response response
     * @param files files
     * @return 结果
     * @throws UnsupportedEncodingException 异常处理
     */
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

    /**
     * 文件删除操作
     * @param oid oid
     * @param jsonResult jsonResult
     * @return 删除结果
     */
    public JsonResult delete(String oid, JsonResult jsonResult){
//        boolean delLog = false;

        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
        if(bulkDataLink == null){
            jsonResult.setCode(-1);
            jsonResult.setResult("err");
            jsonResult.setMessage("file not exist");
            return jsonResult;
        }
        //删除文件夹或文件,如删除批量上传的文件，则所有文件的文件夹也删除
//        for (int i=0;i<bulkDataLink.getDataOids().size();i++){
//            DataListCom dataListCom = dataListComDao.findFirstByOid(bulkDataLink.getDataOids().get(i));
//            String delPath = dataListCom.getPath();
//            delLog = deleteFolder(delPath);
//        }
        //删除dataListCom中文件只引用系数减一

        for (int i=0;i<bulkDataLink.getDataOids().size();i++){
            DataListCom dataListCom = dataListComDao.findFirstByOid(bulkDataLink.getDataOids().get(i));
            if(dataListCom == null){
                jsonResult.setCode(-1);
                jsonResult.setMessage("(dataListCom) Source file not found");
                jsonResult.setResult("err");
                return jsonResult;
            }
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
                //考虑到引用系数已经为0还要删除的情况
                jsonResult.setCode(-1);
                jsonResult.setResult("err");
                jsonResult.setMessage("The file reference coefficient is 0 and cannot be deleted");
                return jsonResult;
//                delLog = false;
//                return delLog;
            }
        }
        //dataOids文件夹删除后删除zip文件夹
        String delPath = bulkDataLink.getPath();
//         delLog = ;

        if (deleteFolder(delPath)){
            //删除对应的数据库内容
            if (bulkDataLink!=null) {
                bulkDataLinkDao.delete(bulkDataLink);
            }
            jsonResult.setResult("suc");
            jsonResult.setCode(1);
            jsonResult.setData("");
            jsonResult.setMessage("delete file success");
            return jsonResult;
        }else {
            jsonResult.setResult("err");
            jsonResult.setCode(-1);
            jsonResult.setMessage("failed to delete zip folder");
            return jsonResult;
        }

//        return delLog;
    }

    /**
     * 根据路径删除指定的目录或文件，无论存在与否
     * @param sPath sPath
     * @return 删除结果
     */
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

    /**
     * 删除单个文件
     * @param sPath sPath
     * @return 结果
     */
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

    /**
     * 删除目录（文件夹）以及目录下的文件
     * @param sPath sPath
     * @return 结果
     */
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

    /**
     * 引用计数++
     * @param oid oid
     */
    public void referenceCountPlusPlus(String oid){
        DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
        int count = dataListCom.getReferenceCount() + 1;
        dataListCom.setReferenceCount(count);
        dataListComDao.save(dataListCom);
    }

    /**
     * 引用计数--
     * @param oid oid
     */
    public void referenceCountMinusMinus(String oid){
        DataListCom dataListCom = dataListComDao.findFirstByOid(oid);
        int count = dataListCom.getReferenceCount() - 1;
        dataListCom.setReferenceCount(count);
        dataListComDao.save(dataListCom);
    }

    /**
     * 对md5值为0且为0时间超30天的文件进行删除，先测试5秒钟的
     */
    @Scheduled(cron = "*/5 * * * * ?")
    //部署时解开，每月一号凌晨一点
//    @Scheduled(cron = "0 0 1 1 * ?")
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

    /**
     * 解压文件
     * @param inputFile inputFile
     * @param destDirPath destDirPath
     * @throws Exception 异常处理
     */
    public void zipUncompress(String inputFile,String destDirPath) throws Exception {
        File srcFile = new File(inputFile);
        if (!srcFile.exists()){
            throw new Exception(srcFile.getPath() + "所指文件不存在");
        }

        ZipFile zipFile = new ZipFile(srcFile);
        Enumeration<?> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            // 如果是文件夹，就创建个文件夹
            if (entry.isDirectory()) {
                String dirPath = destDirPath + "/" + entry.getName();
                srcFile.mkdirs();
            } else {
                // 如果是文件，就先创建一个文件，然后用io流把内容copy过去
                File targetFile = new File(destDirPath + "/" + entry.getName());
                // 保证这个文件的父文件夹必须要存在
                if (!targetFile.getParentFile().exists()) {
                    targetFile.getParentFile().mkdirs();
                }
                targetFile.createNewFile();
                // 将压缩文件内容写入到这个文件中
                InputStream is = zipFile.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(targetFile);
                int len;
                byte[] buf = new byte[1024];
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                // 关流顺序，先打开的后关闭

                fos.close();
                is.close();
            }
        }
        zipFile.close();
    }

    /**
     * 执行python脚本之后删除解压后的文件
     * @param zipFile zipFile
     * @param zipPath zipPath
     * @return 结果
     */
    public Boolean deleteZipUncompress(String zipFile,String zipPath){
        boolean delLog = false;
        File zipFilePath = new File(zipPath);
        File[] files = zipFilePath.listFiles();
        assert files != null;
        for (File file: files){
            //先进行判断，避免删掉原zip文件
            String zipName = intercept(file.getAbsolutePath());
            String zipFileName = intercept(zipFile);
            if (!zipName.equals(zipFileName)){
                //判断是文件还是文件夹
                if (file.isFile()){
                    deleteFile(file.getAbsolutePath());
                }else {
                    deleteDirectory(file.getAbsolutePath());
                }
            }
        }
        delLog = true;
        log.info("删除结束");
        return delLog;
    }

    /**
     * 截取路径/\后的元素
     * @param input input
     * @return 结果
     */
    public String intercept(String input){
        int index = input.lastIndexOf("\\");
        int index1 = input.lastIndexOf("/");
        if (index!=-1){
            return input.substring(index+1,input.length());
        }else{
            return input.substring(index1+1,input.length());
        }
    }

    /**
     * 断点续传
     * @param oid oid
     * @param savePath savePath
     * @param response response
     * @return 结果
     * @throws InterruptedException 异常处理
     * @throws IOException 异常处理
     */
    public Boolean downBPContinue(String oid,String savePath, HttpServletResponse response) throws InterruptedException, IOException {
        boolean downLoadLog = false;
        File downFile = null;
        File tmpFile = null;

        //下载方法
        BulkDataLink bulkDataLink = bulkDataLinkDao.findFirstByZipOid(oid);
        String fileName = bulkDataLink.getPath() + "/" + bulkDataLink.getZipOid() + ".zip";
        InputStream fis = null;
        BufferedInputStream bis = null;
        byte[] buffer = new byte[1024];
        int len = -1;
        log.info(fileName);

        File file = new File(fileName);
        long fileLength = file.length();

        fis = new FileInputStream(file);

        String bpFileName = bulkDataLink.getName();
        String bpTmpFileName = bpFileName + "_tmp";
        //下载文件和临时文件
        downFile = new File(bpFileName);
        tmpFile = new File(bpTmpFileName);
        int threadNum = 5;
        long[] startPos = new long[threadNum];//保存每个线程下载数据的起始位置
        long[] endPos = new long[threadNum];//保存每个线程下载数据的截至位置
        long blockFileSize = fileLength%threadNum == 0?fileLength/threadNum:fileLength/threadNum+1;
        log.info("blockSize: " + blockFileSize + " fileLength: " + fileLength);


        latch = new CountDownLatch(threadNum);
        if (downFile.exists()&&downFile.length() == fileLength&&!tmpFile.exists()){
            log.info("file is already exist");
            return false;
        }else {
            //设置start and end
            setBreakPoint(startPos,endPos,threadNum, blockFileSize, tmpFile,fileLength);
            //创建可缓存线程池
            ExecutorService executorService = Executors.newCachedThreadPool();
            for (int i = 0; i < threadNum; i++) {
                //执行线程
                executorService.execute(new BPContinueThread(startPos[i], endPos[i], file, bpFileName, bpTmpFileName, i, latch, fis, fileName, blockFileSize));
            }
            latch.await();
            executorService.shutdown();
        }
        //下载完成后，判断是否完整，并删除临时文件
        if (downFile.length() == fileLength){
            if (tmpFile.exists()){
                log.info("delete tmp file");
                tmpFile.delete();
            }
        }
        return downLoadLog;
    }

    /**
     * 设置断点续传断点
     * @param startPos startPos
     * @param endPos endPos
     * @param threadNum threadNum
     * @param blockFileSize blockFileSize
     * @param tmpFile tmpFile
     * @param fileLength fileLength
     */
    private void setBreakPoint(long[] startPos,long[] endPos, int threadNum, long blockFileSize, File tmpFile, long fileLength){
        RandomAccessFile ranTmpFile = null;
        try {
            if (tmpFile.exists()){
                log.info("continue download");
                ranTmpFile = new RandomAccessFile(tmpFile, "rw");
                for (int i=0;i<threadNum;i++){
                    ranTmpFile.seek(8*i+8);
                    startPos[i] = ranTmpFile.readLong();

                    ranTmpFile.seek(8*(i+1000) + 16);
                    endPos[i] = ranTmpFile.readLong();

                    log.info("the array content in the exit file: ");
                    log.info("the thread" + (i+1) + " startPos: " + startPos[i] + ", endPos: " + endPos[i]);
                }
            }else {
                log.info("the tmpfile is not available!!");
                ranTmpFile = new RandomAccessFile(tmpFile, "rw");
                //未续传时初始化
                for (int i = 0; i < threadNum; i++) {
                    startPos[i] = i * blockFileSize;
                    if (i == threadNum - 1) {
                        endPos[i] = fileLength;
                    } else {
                        endPos[i] = blockFileSize * (i + 1) - 1;
                    }
                    ranTmpFile.seek(8 * i + 8);
                    ranTmpFile.writeLong(startPos[i]);

                    ranTmpFile.seek(8 * (i + 1000) + 16);
                    ranTmpFile.writeLong(endPos[i]);

                    log.info("the Array content : ");
                    log.info("thre thread" + (i) + " startPos:" + startPos[i] + ", endPos: " + endPos[i]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
           try {
               if (ranTmpFile!=null){
                   ranTmpFile.close();
               }
           } catch (IOException e) {
               e.printStackTrace();
           }
        }

    }

    /**
     * 分割文件
     * @param fileName fileName
     * @param byteSize byteSize
     * @return 分割结果
     */
    public List<String> splitBySize(String fileName, int byteSize) {
        List<String> parts = new ArrayList<String>();
        File file = new File(fileName);
        int count = (int) Math.ceil(file.length() / (double) byteSize);
        int countLen = (count + "").length();

        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(5,10,1,
                TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(count * 2));
        for (int i=0;i<count;i++){
            String partFileName = file.getParent() + File.separator+  file.getName() + "."
                    + leftPad((i + 1) + "", countLen, '0') + ".part";
            threadPool.execute(new SplitRunnable(byteSize, i * byteSize,partFileName, file));
            parts.add(partFileName);
        }
        return parts;
    }

    /**
     * 合并文件
     * @param fileLength fileLength
     * @param dirPath dirPath
     * @param partFileSuffix partFileSuffix
     * @param partFileSize partFileSize
     * @param mergeFileName mergeFileName
     * @throws IOException 异常处理
     */
    public void mergePartFiles(long fileLength, String dirPath, String partFileSuffix, int partFileSize, String mergeFileName) throws IOException {
        ArrayList<File> partFiles = FileUtil.getDirFiles(dirPath,partFileSuffix);
        Collections.sort(partFiles, new FileUtil.FileComparator());
        RandomAccessFile randomAccessFile = new RandomAccessFile(mergeFileName, "rw");
        randomAccessFile.setLength(fileLength);
        log.info("r_length:  "+randomAccessFile.length());
        randomAccessFile.close();

        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(partFiles.size(), partFiles.size() * 3, 1, TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(partFiles.size() * 2));

        for (int i=0;i<partFiles.size();i++){
            threadPool.execute(new MergeRunnable(i * partFileSize,mergeFileName, partFiles.get(i)));
        }
    }


    /**
     * 下载数据到dirPath中
     * @param dataUrl 待下载数据url
     * @param destDirPath 下载数据至的文件夹
     * @return 下载是否成功
     */
    public Boolean downloadContainer(String dataUrl, String destDirPath) throws IOException {
        String filePath = null;
        boolean isDownload = false;
        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        String fileName = null;
        if (dataUrl!=null){
            URL url = new URL(dataUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(60000);
            //通过conn取得文件名称
            String raw = conn.getHeaderField("Content-Disposition");
            if(raw!=null&&raw.indexOf("=")>0){
                fileName = raw.split("=")[1];
                fileName = new String(fileName.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            }
            inputStream = conn.getInputStream();
        }

        File testData = new File(destDirPath);
        if(!testData.exists()){
            testData.mkdirs();
        }
        String path = destDirPath + "/" + fileName ;
        File localFile = new File(path);
        try {
            //将数据下载至resourcePath下
            if (localFile.exists()) {
                //如果文件存在删除文件
                boolean delete = localFile.delete();
            }
            //创建文件
            if (!localFile.exists()) {
                //如果文件不存在，则创建新的文件
                localFile.createNewFile();
            }

            fileOutputStream = new FileOutputStream(localFile);
            byte[] bytes = new byte[1024];
            int len = -1;
            while ((len = inputStream.read(bytes)) != -1) {
                fileOutputStream.write(bytes, 0, len);
            }
            fileOutputStream.close();
            inputStream.close();
            isDownload = true;

        } catch (FileNotFoundException e){
            e.printStackTrace();
        }catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        return isDownload;
    }


    /**
     * 每周备份数据库
     */
    @Scheduled(cron = "*/5 * * * * ?")
    //部署时解开，每月一号凌晨一点
//    @Scheduled(cron = "0 0 1 1 * ?")
    private void CopyDatabase(){
        OperationCopy();
    }
    private void OperationCopy(){
        List<BulkDataLink> bulkDataLinks = bulkDataLinkDao.findAll();

        //init
        TimeStamp newTime = new TimeStamp();
        Date date = new Date();
        newTime.setTime(date);
        timeStampDao.insert(newTime);

//        List<TimeStamp> dates = timeStampDao.findAll();
//        TimeStamp timeStamp = dates.get(dates.size()-1);
//        Date preTime = timeStamp.getTime();
//        JSONArray bulk = new JSONArray();
//        JSONArray com = new JSONArray();
//        for(int i=bulkDataLinks.size()-1;i>=0;i--){
//            if(bulkDataLinks.get(i).getDate().compareTo(preTime) > 0){
//                bulk.add(bulkDataLinks.get(i));
//                List<String> dataOids = bulkDataLinks.get(i).getDataOids();
//                for(String dataOid:dataOids){
//                    com.add(dataListComDao.findFirstByOid(dataOid));
//                }
//            }else {
//                break;
//            }
//        }
//        String url = "http://221.226.60.2:8088/copyDataContainerBase";
//        String response = null;
//        MultiValueMap<String, Object> part = new LinkedMultiValueMap<>();
//        part.add("bulk", bulk);
//        part.add("com", com);
//        HttpHeaders headers = new HttpHeaders();
//        headers.add("Content-Type", "application/json");
//        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<MultiValueMap>(part, headers);
//        RestTemplate restTemplate = new RestTemplate();
//        try {
//            response = restTemplate.postForObject(url, requestEntity, String.class);
//        } catch (ResourceAccessException e){
//            log.info("code: 1, request timeout!");
//        }
//        log.info(response);
//        TimeStamp timeStamp1 = dates.get(dates.size()-1);
//        //更新时间戳时间
////        timeStamp1.setTime(new Date());
////        dates.set(dates.size()-1, timeStamp1);
//        timeStampDao.save(timeStamp1);
    }
}