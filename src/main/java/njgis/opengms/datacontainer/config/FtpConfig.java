package njgis.opengms.datacontainer.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.stereotype.Service;

import javax.naming.ldap.PagedResultsControl;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;

/**
 * @Author mingyuan
 * @Date 2020.09.15 10:41
 */
@Slf4j
@Service
public class FtpConfig {
//    private String hostname;
//    private int port;
//    private String username;
//    private String password;

//    public FtpConfig(String hostname, int port, String username, String password){
//        this.hostname = hostname;
//        this.port = port;
//        this.username = username;
//        this.password = password;
//    }


    public void initFtpClient(String hostname,int port, String username, String password) throws SocketException, IOException {
        FTPClient ftpClient = new FTPClient();
        ftpClient.setControlEncoding("utf-8");
        log.info("connecting...ftp服务器:" + hostname + ":" + port);
        ftpClient.connect(hostname, port); // 连接ftp服务器
        ftpClient.login(username, password); // 登录ftp服务器
        int replyCode = ftpClient.getReplyCode(); // 是否成功登录服务器
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            log.info("connect failed...ftp服务器:" + hostname + ":" + port);
        }
        log.info("connect successfu...ftp服务器:" + hostname + ":" + port);

    }

    /**
     * 上传文件
     *
     * @param pathname
     *            ftp服务保存地址
     * @param fileName
     *            上传到ftp的文件名
     * @param inputStream
     *            输入文件流
     * @return
     * @throws IOException
     */
    public boolean uploadFile(String pathname, String fileName, InputStream inputStream,
                              String hostname,int port, String username, String password) throws IOException {
        boolean flag = false;
        FTPClient ftpClient = new FTPClient();
        initFtpClient(hostname, port, username, password);
        try {
            log.info("开始上传文件");
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
//          CreateDirecroty(pathname);

            boolean changeWorkingDirectory = ftpClient.changeWorkingDirectory(pathname);
            if(changeWorkingDirectory) {
                log.info("进入文件"+pathname+"夹成功.");
            }else {
                log.info("进入文件"+pathname+"夹失败.开始创建文件夹");
                boolean makeDirectory = ftpClient.makeDirectory(pathname);
                if(makeDirectory) {
                    log.info("创建文件夹"+pathname+"成功");
                    boolean changeWorkingDirectory2 = ftpClient.changeWorkingDirectory(pathname);
                    if(changeWorkingDirectory2) {
                        log.info("进入文件"+pathname+"夹成功.");
                    }
                }else {
                    log.info("创建文件夹"+pathname+"失败");
                }
            }
            ftpClient.storeFile(fileName, inputStream);
            inputStream.close();
            ftpClient.logout();
            flag = true;
            if (flag) {

                log.info("上传文件成功");
            }
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return true;
    }
}
