package njgis.opengms.datacontainer.thread;

import java.io.*;

/**
 * @Author mingyuan
 * @Date 2020.07.24 15:37
 */
public class MergeRunnable implements Runnable{
    long startPos;
    String mergeFileName;
    File partFile;

    public MergeRunnable(long startPos, String mergeFileName, File partFile){
        this.startPos = startPos;
        this.mergeFileName = mergeFileName;
        this.partFile = partFile;
    }

    @Override
    public void run() {
        RandomAccessFile rFile;
        try {
            rFile = new RandomAccessFile(mergeFileName, "rw");
            rFile.seek(startPos);
            FileInputStream fileInputStream = new FileInputStream(partFile);
            byte[] bytes = new byte[fileInputStream.available()];
            fileInputStream.read(bytes);
            fileInputStream.close();
            rFile.write(bytes);
            rFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
