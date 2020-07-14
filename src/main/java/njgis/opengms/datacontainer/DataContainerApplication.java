package njgis.opengms.datacontainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Author mingyuan
 * @Date 2020.06.11 15:02
 */

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class DataContainerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataContainerApplication.class, args);
    }
}
