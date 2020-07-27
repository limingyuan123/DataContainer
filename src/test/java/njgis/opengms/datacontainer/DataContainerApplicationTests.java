package njgis.opengms.datacontainer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @Author mingyuan
 * @Date 2020.06.11 15:03
 */

@RunWith(SpringRunner.class)
@SpringBootTest
public class DataContainerApplicationTests {

    @Test
    public void contextLoads() {
    }

    @Test
    public void pythonScriptTest(){
        int a = 18;
        int b = 23;
        try {
            String[] args = new String[] { "python", "E:\\upload\\upload_ogms\\plusplus.py", String.valueOf(a), String.valueOf(b) };
            Process proc = Runtime.getRuntime().exec(args);// 执行py文件

            BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            String line = null;
            while ((line = in.readLine()) != null) {
                System.out.println(line);
            }
            in.close();
            proc.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}