package njgis.opengms.datacontainer;

import lombok.extern.slf4j.Slf4j;
import njgis.opengms.datacontainer.utils.MultiTheradDownload;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author mingyuan
 * @Date 2020.07.21 21:35
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class DownloadTest {
    @Test
    public void main() {
        int threadNum = 4;
//        String filepath = "http://down.sandai.net/thunder9/Thunder9.1.40.898.exe";
        String filepath = "http://192.168.47.130:8082/data?oid=90efe866-d424-44d5-bc1f-b5853aefa8e7";
//        String filepath = "http://192.168.47.130:8082/data?oid=857ed9f1-cb61-4e70-9444-e4a23af27311";
        MultiTheradDownload load = new MultiTheradDownload(filepath ,threadNum);
        load.downloadPart();
    }
}
