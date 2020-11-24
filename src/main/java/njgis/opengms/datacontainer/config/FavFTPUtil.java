package njgis.opengms.datacontainer.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.jsoup.helper.HttpConnection;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;

/**
 * @Author mingyuan
 * @Date 2020.09.14 19:46
 */
@Slf4j
public class FavFTPUtil {
    /**
     * 上传文件（可供Action/Controller层使用）
     *
     * @param hostname
     *            FTP服务器地址
     * @param port
     *            FTP服务器端口号
     * @param username
     *            FTP登录帐号
     * @param password
     *            FTP登录密码
     * @param pathname
     *            FTP服务器保存目录
     * @param fileName
     *            上传到FTP服务器后的文件名称
     * @param inputStream
     *            输入文件流
     * @return
     */
    public static boolean uploadFile(String hostname, int port, String username, String password, String pathname,
                                     String fileName, InputStream inputStream) {
        boolean flag = false;
        FTPClient ftpClient = new FTPClient();
        ftpClient.setControlEncoding("UTF-8");
        ftpClient.enterLocalPassiveMode();
        try {
            // 连接FTP服务器
            ftpClient.connect(hostname, port);
            // 登录FTP服务器
            ftpClient.login(username, password);
            // 是否成功登录FTP服务器
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                return flag;
            }

            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
            ftpClient.makeDirectory(pathname);
            boolean change = ftpClient.changeWorkingDirectory("/ogms");
            if (change){
                log.info("进入"+pathname+"文件夹成功.");
            }
            boolean stored = ftpClient.storeFile(new String(fileName.getBytes("UTF-8"),"iso-8859-1"), inputStream);
            if (stored){
                log.info("storeFile success");
            }
            inputStream.close();
            ftpClient.logout();
            flag = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return flag;
    }

    /**
     * 上传文件（可对文件进行重命名）
     *
     * @param hostname
     *            FTP服务器地址
     * @param port
     *            FTP服务器端口号
     * @param username
     *            FTP登录帐号
     * @param password
     *            FTP登录密码
     * @param pathname
     *            FTP服务器保存目录
     * @param filename
     *            上传到FTP服务器后的文件名称
     * @param originfilename
     *            待上传文件的名称（绝对地址）
     * @return
     */
    public static boolean uploadFileFromProduction(String hostname, int port, String username, String password,
                                                   String pathname, String filename, String originfilename) {
        boolean flag = false;
        try {
            InputStream inputStream = new FileInputStream(new File(originfilename));
            flag = uploadFile(hostname, port, username, password, pathname, filename, inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 上传文件（不可以进行文件的重命名操作）
     *
     * @param hostname
     *            FTP服务器地址
     * @param port
     *            FTP服务器端口号
     * @param username
     *            FTP登录帐号
     * @param password
     *            FTP登录密码
     * @param pathname
     *            FTP服务器保存目录
     * @param file
     *            待上传文件
     * @return
     */
    public static boolean uploadFileFromProduction(String hostname, int port, String username, String password,
                                                   String pathname, MultipartFile file) {
        boolean flag = false;
        try {
            String fileName = file.getOriginalFilename();
            InputStream inputStream = file.getInputStream();
            log.info(inputStream.available()+"");
            flag = uploadFile(hostname, port, username, password, pathname, fileName, inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 删除文件
     *
     * @param hostname
     *            FTP服务器地址
     * @param port
     *            FTP服务器端口号
     * @param username
     *            FTP登录帐号
     * @param password
     *            FTP登录密码
     * @param pathname
     *            FTP服务器保存目录
     * @param filename
     *            要删除的文件名称
     * @return
     */
    public static boolean deleteFile(String hostname, int port, String username, String password, String pathname,
                                     String filename) {
        boolean flag = false;
        FTPClient ftpClient = new FTPClient();
        try {
            // 连接FTP服务器
            ftpClient.connect(hostname, port);
            // 登录FTP服务器
            ftpClient.login(username, password);
            // 验证FTP服务器是否登录成功
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                return flag;
            }
            // 切换FTP目录
            ftpClient.changeWorkingDirectory(pathname);
            ftpClient.dele(filename);
            ftpClient.logout();
            flag = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } catch (IOException e) {

                }
            }
        }
        return flag;
    }

    /**
     * 下载文件
     *
     * @param hostname
     *            FTP服务器地址
     * @param port
     *            FTP服务器端口号
     * @param username
     *            FTP登录帐号
     * @param password
     *            FTP登录密码
     * @param pathname
     *            FTP服务器文件目录
     * @param filename
     *            文件名称
     * @param localpath
     *            下载后的文件路径
     * @return
     */
    public static boolean downloadFile(String hostname, int port, String username, String password, String pathname,
                                       String filename, String localpath) {
        boolean flag = false;
        FTPClient ftpClient = new FTPClient();
        try {
            // 连接FTP服务器
            ftpClient.connect(hostname, port);
            // 登录FTP服务器
            ftpClient.login(username, password);
            // 验证FTP服务器是否登录成功
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                return flag;
            }
            //根据名称拼出ftp下载链接
//            String strUrl = "ftp://" + hostname + pathname + "/" + filename;
//            HttpURLConnection conn = null;
//            try {
//                URL url = new URL(strUrl);
//                conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("GET");
//                conn.setConnectTimeout(20*1000);
//                InputStream inputStream = conn.getInputStream();
//                long length = conn.getContentLength();
//                log.info(length+"");
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

//            String filePath =


            // 切换FTP目录
            ftpClient.changeWorkingDirectory(pathname);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            for (FTPFile file : ftpFiles) {
                if (filename.equalsIgnoreCase(file.getName())) {
                    File localFile = new File(localpath + "/" + file.getName());
                    OutputStream os = new FileOutputStream(localFile);
                    ftpClient.retrieveFile(file.getName(), os);
                    os.close();
                }
            }
            ftpClient.logout();
            flag = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } catch (IOException e) {

                }
            }
        }
        return flag;
    }

}
