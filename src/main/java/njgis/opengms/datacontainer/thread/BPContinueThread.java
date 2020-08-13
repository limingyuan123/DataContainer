package njgis.opengms.datacontainer.thread;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.concurrent.CountDownLatch;

/**
 * @Author mingyuan
 * @Date 2020.07.27 16:44
 */
@Slf4j
public class BPContinueThread implements Runnable{
    private long startPos;
    private long endPos;
    private RandomAccessFile downloadFile = null;
    private File tmpFile = null;
    private RandomAccessFile ranTmpFile = null;
    private String bpFileName;
    private String ranTmpFileName;
    private Integer id;
    private InputStream fis = null;
    private CountDownLatch latch = null;
    private String fileName;
    private long blockFileSize;

    public BPContinueThread(long startPos, long endPos, File tmpFile, String bpFileName, String ranTmpFileName,
                            int id, CountDownLatch latch, InputStream fis,String fileName,long blockFileSize){
        this.startPos = startPos;
        this.endPos = endPos;
        this.tmpFile = tmpFile;
        this.bpFileName = bpFileName;
        this.ranTmpFileName = ranTmpFileName;
        this.id = id;
        this.fis = fis;
        this.latch = latch;
        this.fileName = fileName;
        this.blockFileSize = blockFileSize;
        try{
            this.downloadFile = new RandomAccessFile(bpFileName,"rw");
            this.ranTmpFile = new RandomAccessFile(ranTmpFileName, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @SneakyThrows
    @Override
    public synchronized void run() {
        int length = 0;

        log.info("thread" + id + " begin download");

        while(true){
          try {
              if (startPos<endPos){
                  //获取该段数据流
                  byte[] downFileByte = interceptStreamAsByteArray(fis,startPos,endPos,fileName,blockFileSize);//截取数据流
                  InputStream downFileIns = new ByteArrayInputStream(downFileByte);//byte[]=>InputStream
//                  InputStream[] inputStreams = new InputStream[5];

                  log.info("thread" + id + " length:----" + (endPos - startPos));
                  downloadFile.seek(startPos);
                  long count = 0L;
                  byte[] buffer = new byte[1024];
                  while ((length = downFileIns.read(buffer))!=-1) {
                      count += length;
                      downloadFile.write(buffer, 0, length);

                      //更新每个线程下载的startPos，并写入临时文件，为断点续传做准备
                      startPos += length;
                      ranTmpFile.seek(8*id+8);
                      ranTmpFile.writeLong(startPos);
                  }
                  log.info("thread" + id + " sum download :" + count);

                  //关闭流
                  downFileIns.close();
                  downloadFile.close();
                  ranTmpFile.close();
              }
              latch.countDown();//自减
              log.info("thread" + id + " download over");
              break;
          } catch (IOException e) {
              e.printStackTrace();
          }
        }
    }
    public static  final int READ_BUFFER_SIZE = 1024;
    public static byte[] interceptStreamAsByteArray(InputStream in, long startPos, long endPos, String fileName,long blockFileSize) throws IOException {
        if (in == null){
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            RandomAccessFile rFile = new RandomAccessFile(fileName, "r");
            OutputStream outputStream = null;
            rFile.seek(startPos);
//            (endPos - startPos)>
//            long byteSize = endPos - startPos;
            long byteSize = blockFileSize;
            byte[] bytes = new byte[(int) byteSize];
            int s = rFile.read(bytes);
//            outputStream.write(bytes, 0, s);
//            outputStream.flush();
//            outputStream.close();

            output.write(bytes, 0, s);


//            byte[] buffer = new byte[READ_BUFFER_SIZE];
//            int len = -1;
//            long temp = endPos - startPos - READ_BUFFER_SIZE;
//            long limit = endPos - startPos;
//            while ((len = in.read(buffer))!= -1){
//                output.write(buffer, 0, len);
//                if (output.size()>temp){
//                    if (output.size() == limit){
//                        break;
//                    }
//                    byte[] buffer2 = new byte[(int) (limit - output.size())];
//                    while ((len = in.read(buffer2))!=-1){
//                        output.write(buffer2, 0, len);
//                        if (output.size() == limit){
//                            break;
//                        }
//                        buffer2 = new byte[(int) (limit - output.size())];
//                    }
//                    break;
//                }
//            }
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }


        return output.toByteArray();
    }
}
