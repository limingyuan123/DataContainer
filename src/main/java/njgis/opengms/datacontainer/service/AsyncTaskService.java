package njgis.opengms.datacontainer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * @Author mingyuan
 * @Date 2020.07.13 15:54
 */
@Service
public class AsyncTaskService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RestTemplate restTemplate;

    @Async
    public void executeAsyncTask(int i){
        System.out.println("线程" + Thread.currentThread().getName() + "执行异步任务：" + i);
    }

    @Async
    public Future<Long> subByAsync() throws Exception{
        long start = System.currentTimeMillis();
        long sum = 0;
        Thread.sleep(5);
        long end = System.currentTimeMillis();
        sum = end - start;
        logger.info("\t 完成任务一");
        return new AsyncResult<>(sum);
    }

    @Async
    public CompletableFuture<String> findUser(String user) throws InterruptedException {
        logger.info("Looking up " + user);
        String url = String.format("https://api.github.com/users/%s", user);
        String results = restTemplate.getForObject(url, String.class);
        // Artificial delay of 3s for demonstration purposes
        Thread.sleep(3000L);
        return CompletableFuture.completedFuture(results);
    }


}
