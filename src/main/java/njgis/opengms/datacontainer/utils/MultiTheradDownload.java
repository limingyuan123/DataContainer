package njgis.opengms.datacontainer.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author mingyuan
 * @Date 2020.07.21 20:17
 */
@Slf4j
public class MultiTheradDownload {
    private String urlStr = null;
    private String bkFileName = null;
    private String bkTmpFileName = null;
    private int threadNum = 0;
    //计数器，主要用来完成缓存文件删除
    private CountDownLatch latch = null;

    private long fileLength = 0l;
    private long threadLength = 0l;
    private long[] startPos;//保存每个线程下载数据的起始位置
    private long[] endPos;//保存每个线程下载数据的截至位置

    private boolean bool = false;
    private URL url = null;

    //有参构造函数，先构造需要的数据
    public MultiTheradDownload(String urlStr, int threadNum) {
        this.urlStr = urlStr;
        this.threadNum = threadNum;
        startPos = new long[this.threadNum];
        endPos = new long[this.threadNum];
        latch = new CountDownLatch(this.threadNum);
    }

    public void downloadPart(){
//        String oid = "212133";
        File file = null;
        File tmpFile = null;

        //设置HTTP网络访问代理
        System.setProperty("http.proxySet", "true");
        System.setProperty("http.proxyHost", "proxy3.bj.petrochina");
        System.setProperty("http.proxyPort", "8080");

        bkFileName = urlStr.substring(urlStr.lastIndexOf('/') + 1, urlStr.contains("?") ? urlStr.lastIndexOf('?') : urlStr.length());
        bkTmpFileName = bkFileName + "_tmp";
        try {
            url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setReadTimeout(60000);
            conn.setConnectTimeout(5000);
            InputStream inputStream = null;
            inputStream = conn.getInputStream();

            //下载文件的总长度
            fileLength = conn.getContentLengthLong();
            //下载文件和临时文件
            file = new File(bkFileName);
            tmpFile = new File(bkTmpFileName);

            //每个线程需要下载的资源大小
            threadLength = fileLength%threadNum == 0?fileLength/threadNum:fileLength/threadNum+1;
            //打印下载信息
            log.info("fileName " + bkFileName + " ," + "fileLength= " + fileLength + " the threadLength= " + threadLength);

            //各个线程在exec线程池中进行，起始位置--结束位置
            if (file.exists()&&file.length() == fileLength){
                log.info("文件已存在");
                return;
            }else {
                setBreakPoint(startPos, endPos, tmpFile);
                ExecutorService exec = Executors.newCachedThreadPool();
                for (int i=0;i<threadNum;i++){
                    exec.execute(new DownloadThread(startPos[i], endPos[i],this,i,tmpFile,latch));
                }
                latch.await();//当你的计数器减为0之前，会在此处一直阻塞
                exec.shutdown();
            }
        }catch (MalformedURLException e){
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //下载完成后，判断文件是否完整，并删除临时文件
        if (file.length() == fileLength){
            if (tmpFile.exists()){
                log.info("删除临时文件");
                tmpFile.delete();
            }
        }
    }

    /*
     * 断点设置方法，当有临时文件时，直接在临时文件中读取上次下载中断时的断点位置。没有临时文件，即第一次下载时，重新设置断点。
     *
     * Rantmpfile.seek()跳转到一个位置的目的是为了让各个断点存储的位置尽量分开。
     *
     * 这是实现断点续传的重要基础。
     */
    private void setBreakPoint(long[] startPos,long[] endPos, File tmpFile){
        RandomAccessFile ranTmpFile = null;
        try {
            if (tmpFile.exists()){
                log.info("继续下载");
                ranTmpFile = new RandomAccessFile(tmpFile, "rw");
                for (int i=0;i<threadNum;i++){
                    ranTmpFile.seek(8*i+8);
                    startPos[i] = ranTmpFile.readLong();

                    ranTmpFile.seek(8*(i+1000) +16);
                    endPos[i] = ranTmpFile.readLong();

                    log.info("the Array content in the exit file: ");
                    log.info("thre thread" + (i + 1) + " startPos:" + startPos[i] + ", endPos: " + endPos[i]);
                }
            }else {
                log.info("the tmpfile is not available!!");
                ranTmpFile = new RandomAccessFile(tmpFile,"rw");

                //最后一个线程的截止位置的大小为请求资源的大小
                for (int i=0;i<threadNum;i++){
                    startPos[i] = threadLength*i;
                    if (i==threadNum-1){
                        endPos[i] = fileLength;
                    }else {
                        endPos[i] = threadLength*(i+1)-1;
                    }

                    ranTmpFile.seek(8*i+8);
                    ranTmpFile.writeLong(startPos[i]);

                    ranTmpFile.seek(8 * (i + 1000) + 16);
                    ranTmpFile.writeLong(endPos[i]);

                    log.info("the Array content: ");
                    log.info("thre thread" + (i + 1) + " startPos:" + startPos[i] + ", endPos: " + endPos[i]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if (ranTmpFile!=null){
                    ranTmpFile.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    /*
     * 实现下载功能的内部类，通过读取断点来设置向服务器请求的数据区间。
     */
    class DownloadThread implements Runnable{
        private long startPos;
        private long endPos;
        private MultiTheradDownload task = null;
        private RandomAccessFile downloadFile = null;
        private int id;
        private File tmpFile = null;
        private RandomAccessFile ranTmpFile = null;
        private CountDownLatch latch = null;

        public DownloadThread(long startPos,long endPos,MultiTheradDownload task,int id,File tmpFile,CountDownLatch latch){
            this.startPos = startPos;
            this.endPos = endPos;
            this.task = task;
            this.tmpFile = tmpFile;
            try{
                this.downloadFile = new RandomAccessFile(this.task.bkFileName,"rw");
                this.ranTmpFile = new RandomAccessFile(this.task.bkTmpFileName,"rw");
            }catch (FileNotFoundException e){
                e.printStackTrace();
            }
            this.id = id;
            this.latch = latch;
        }

        @Override
        public void run() {
            HttpURLConnection httpCon = null;
            InputStream inputStream = null;
            int length = 0;

            log.info("线程" + id + " 开始下载!!");

            while (true){
                try {
                    httpCon = (HttpURLConnection) task.url.openConnection();
                    httpCon.setRequestMethod("GET");

                    httpCon.setConnectTimeout(20000);
                    httpCon.setReadTimeout(20000);

                    if (startPos<endPos){
                        //向服务器请求指定区间段的数据
                        httpCon.setRequestProperty("Range","bytes=" + startPos + "-" + endPos);
                        log.info("线程 " + id + "长度：----" + (endPos - startPos));
                        downloadFile.seek(startPos);
                        if (httpCon.getResponseCode()!= HttpURLConnection.HTTP_OK && httpCon.getResponseCode()!=HttpURLConnection.HTTP_PARTIAL){
                            this.task.bool = true;
                            httpCon.disconnect();
                            downloadFile.close();
                            log.info("线程 ---" + id + " 下载完成!!!");
                            latch.countDown();//计数器自减
                            break;
                        }
                        inputStream = httpCon.getInputStream();//获取服务器返回的资源流
                        long count = 0l;
                        byte[] buffer = new byte[1024];
                        while (!this.task.bool && (length = inputStream.read(buffer))!=-1){
                            count += length;
                            downloadFile.write(buffer, 0, length);

                            //不断更新每个线程下载资源的起始位置，并写入临时文件；为断点续传做准备
                            startPos += length;
                            ranTmpFile.seek(8*id+8);
                            ranTmpFile.writeLong(startPos);
                        }
                        log.info("线程" +id+" 总下载大小：" + count);

                        //关闭流
                        inputStream.close();
                        httpCon.disconnect();
                        downloadFile.close();
                        ranTmpFile.close();
                    }
                    latch.countDown();//计数器自减
                    log.info("线程 " + id + "下载完成!!");
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    try {
                        if (inputStream!=null){
                            inputStream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}