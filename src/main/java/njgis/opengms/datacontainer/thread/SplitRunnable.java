package njgis.opengms.datacontainer.thread;

import org.dom4j.io.OutputFormat;

import java.io.*;

/**
 * @Author mingyuan
 * @Date 2020.07.24 14:35
 */
public class SplitRunnable implements Runnable{
    int byteSize;
    String partFileName;
    File originFile;
    long startPos;

    public SplitRunnable(int byteSize,int startPos,String partFileName,File originFile){
        this.byteSize = byteSize;
        this.startPos = startPos;
        this.partFileName = partFileName;
        this.originFile = originFile;
    }

    @Override
    public void run() {
        RandomAccessFile rFile;
        OutputStream outputStream;
        try {
            rFile = new RandomAccessFile(originFile, "r");
            byte[] bytes = new byte[byteSize];
            rFile.seek(startPos);
            int s = rFile.read(bytes);
            outputStream = new FileOutputStream(partFileName);
            outputStream.write(bytes,0,s);
            outputStream.flush();
            outputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
