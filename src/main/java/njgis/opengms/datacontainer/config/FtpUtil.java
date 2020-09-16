package njgis.opengms.datacontainer.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;
import java.net.SocketException;

/**
 * @Author mingyuan
 * @Date 2020.09.11 14:26
 */
@Slf4j
public class FtpUtil {
    /**
     * 获取FTPClient对象
     * @param ftpHost       FTP主机服务器
     * @param ftpPassword   FTP 登录密码
     * @param ftpUserName   FTP登录用户名
     * @param ftpPort       FTP端口 默认为21
     * @return
     */
    public static FTPClient getFTPClient(String ftpHost, String ftpUserName, String ftpPassword, int ftpPort) {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient = new FTPClient();
            ftpClient.connect(ftpHost, ftpPort);// 连接FTP服务器
            ftpClient.login(ftpUserName, ftpPassword);// 登陆FTP服务器
            ftpClient.setControlEncoding("UTF-8"); // 中文支持
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
            if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                log.info("未连接到FTP，用户名或密码错误。");
                ftpClient.disconnect();
            } else {
                log.info("FTP连接成功。");
            }
        } catch (SocketException e) {
            e.printStackTrace();
            log.info("FTP的IP地址可能错误，请正确配置。");
        } catch (IOException e) {
            e.printStackTrace();
            log.info("FTP的端口错误,请正确配置。");
        }
        return ftpClient;
    }

    /**
     * 从FTP服务器下载文件
     * @param ftpHost FTP IP地址
     * @param ftpUserName FTP 用户名
     * @param ftpPassword FTP用户名密码
     * @param ftpPort FTP端口
     * @param ftpPath FTP服务器中文件所在路径 格式： ftptest/aa
     * @param localPath 下载到本地的位置 格式：H:/download
     * @param fileName FTP服务器上要下载的文件名称
     * @param targetFileName  FTP服务器上要下载的文件名称
     */
    public static void downloadFtpFile(String ftpHost, String ftpUserName, String ftpPassword, int ftpPort, String ftpPath,
                                       String localPath, String fileName, String targetFileName) {

        FTPClient ftpClient = null;
        try {
            ftpClient = getFTPClient(ftpHost, ftpUserName, ftpPassword, ftpPort);
            ftpClient.changeWorkingDirectory(ftpPath);

            String f_ame = new String(fileName.getBytes("GBK"), FTP.DEFAULT_CONTROL_ENCODING);//编码文件格式,解决中文文件名

            File localFile = new File(localPath + File.separatorChar + targetFileName);
            OutputStream os = new FileOutputStream(localFile);
            ftpClient.retrieveFile(f_ame, os);
            os.close();
            ftpClient.logout();

        } catch (FileNotFoundException e) {
            log.error("没有找到" + ftpPath + "文件");
            e.printStackTrace();
        } catch (SocketException e) {
            log.error("连接FTP失败.");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("文件读取错误。");
            e.printStackTrace();
        }
    }

    /**
     * Description: 向FTP服务器上传文件
     * @param ftpHost  FTP服务器hostname
     * @param ftpPort  FTP服务器端口
     * @param ftpUserName FTP登录账号
     * @param ftpPassword  FTP登录密码
     * @param ftpPath  FTP服务器文件存放路径。例如分日期存放：/2015/01/01。文件的路径为basePath+filePath
     * @param filename  上传到FTP服务器上的文件名
     * @param input 输入流
     * @return  成功返回true，否则返回false
     */
    public static boolean uploadFile(String ftpHost, String ftpUserName, String ftpPassword, int ftpPort, String ftpPath,
                                     String filename, InputStream input) {
        boolean result = false;
        FTPClient ftpClient = new FTPClient();
        try {
            int reply;
            ftpClient = getFTPClient(ftpHost, ftpUserName, ftpPassword, ftpPort);
            ftpClient.changeWorkingDirectory(ftpPath);

            reply = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect();
                return result;
            }

            filename = new String(filename.getBytes("GBK"), FTP.DEFAULT_CONTROL_ENCODING);//编码文件名，支持中文文件名
            //上传文件
            if (!ftpClient.storeFile(filename, input)) {
                return result;
            }
            input.close();
            ftpClient.logout();
            result = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException ioe) {
                }
            }
        }
        return result;
    }


}
