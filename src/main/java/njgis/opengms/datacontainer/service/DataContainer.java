package njgis.opengms.datacontainer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
/**
 * @Auther mingyuan
 * @Data 2020.06.13 16:48
 */
@Service
@Slf4j
public class DataContainer {

    public boolean uploadOGMS(String ogmsPath, MultipartFile[] files) {

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
                } catch (IOException e) {
                    log.error("InputStream or OutputStream close error : {}", e);
                    return false;
                }
            }
        }
            return true;
    }

}
