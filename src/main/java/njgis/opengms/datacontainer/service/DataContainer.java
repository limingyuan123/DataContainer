package njgis.opengms.datacontainer.service;

import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.bean.JsonResult;
import njgis.opengms.datacontainer.dao.DataListDao;
import njgis.opengms.datacontainer.entity.DataList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @Author mingyuan
 * @Date 2020.06.13 16:48
 */
@Service
@Slf4j
public class DataContainer {
    @Autowired
    DataListDao dataListDao;

    @Value("${resourcePath}")
    private String resourcePath;

    public boolean uploadOGMS(String ogmsPath,MultipartFile[] files) {

        for (int i=0;i<files.length;i++) {
            MultipartFile file = files[i];

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
        }
            return true;
    }

    public boolean uploadOGMSMulti(String ogmsPath, String uuid, MultipartFile[] files){
        BufferedInputStream bis = null;
//        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        InputStream inputStream = null;
        DataList dataList = new DataList();
        try {
            File filePath = new File(ogmsPath);
            if (!filePath.exists()){
                filePath.mkdirs();
            }

//            File zip = File.createTempFile(uuid, ".zip",filePath);
            String fileName = uuid + ".zip";
            File zip = new File(filePath, fileName);
            zip.createNewFile();
            zos = new ZipOutputStream(new FileOutputStream(zip));
            byte[] bufs = new byte[1024 * 10];


            //如果为多个，则将上传的文件进行压缩，之后进行上传，配置文件不压缩
            for (int i = 0; i < files.length; i++) {
                //有些上传文件无后缀，筛选无后缀文件
                boolean isMatchSuffix = files[i].getOriginalFilename().contains(".");
                if (isMatchSuffix == true){
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
                String streamfilename = files[i].getOriginalFilename();

                ZipEntry zipEntry = new ZipEntry(streamfilename);
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
        uploadOGMS(ogmsPath,files);
        return true;
    }

    public boolean downLoad(String uid, HttpServletResponse response) throws UnsupportedEncodingException {
        boolean downLoadLog = false;
        DataList dataList = dataListDao.findFirstByUid(uid);
        //判断文件个数,单文件不压缩，多文件压缩
        String downLoadPath = dataList.getPath();
        if (dataList.getFileList().size() == 1){
            String fileSingle = dataList.getFileList().get(0);
            File file = new File(fileSingle);
            String suffix = fileSingle.substring(fileSingle.lastIndexOf(".")+1);
            String fileName = dataList.getName() + "." + suffix;//下载时的文件名称
            if (file.exists()){
                downLoadLog = downLoadFile(response, file, fileName);
            }
        }else {
            String fileZip = dataList.getUid() + ".zip";
            String fileName = dataList.getName() + ".zip";
            File file = new File(downLoadPath + "/" + fileZip);
            if (file.exists()) {
                downLoadLog = downLoadFile(response,file,fileName);
            }
        }
        return downLoadLog;
    }

    public boolean downLoadFile(HttpServletResponse response, File file, String fileName) throws UnsupportedEncodingException {
        Boolean downLoadLog = false;
        response.setContentType("application/force-download");
        response.addHeader("Content-Disposition", "attachment;fileName=" + new String(fileName.getBytes("utf-8"),"ISO8859-1"));
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

    public JsonResult downLoadBulkFile(HttpServletResponse response, File[] files) throws UnsupportedEncodingException {
        Boolean downLoadLog = false;
        JsonResult jsonResult = new JsonResult();

        byte[] buffer = new byte[1024*10];

        ZipOutputStream zos = null;
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        InputStream inputStream = null;

        String zipUuid = UUID.randomUUID().toString();
        String zipPath = resourcePath + "/" + zipUuid;
        String zipFileName = zipUuid + ".zip";

        //首先对files进行压缩
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
        response.addHeader("Content-Disposition", "attachment;fileName=" + new String(fileName.getBytes("utf-8"),"ISO8859-1"));

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
}
