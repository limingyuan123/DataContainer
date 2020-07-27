package njgis.opengms.datacontainer;

import njgis.opengms.datacontainer.service.AsyncTaskService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @Author mingyuan
 * @Date 2020.07.13 15:57
 */
@RunWith(SpringRunner.class)
@SpringBootTest
//测试多线程代码
public class ThreadApplicationTests {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AsyncTaskService asyncTaskService;

    @Test
    public void contextLoads(){

    }

    @Test
    public void threadTest(){
        for (int i=0;i<20;i++){
            asyncTaskService.executeAsyncTask(i);
        }
    }

    @Test
    public void threadTest2() throws Exception {
        //调用异步方法
        Future<Long> task = asyncTaskService.subByAsync();
        //接受异步方法结果
        while (true){
            if (task.isDone()){
                long async = task.get();
                logger.info("异步任务执行时间是:" + async + " (毫秒) ");
                break;
            }
        }
    }

    @Test
    public void asyncTest() throws InterruptedException, ExecutionException {
        // Start the clock
        long start = System.currentTimeMillis();

        // Kick of multiple, asynchronous lookups
        CompletableFuture<String> page1 = asyncTaskService.findUser("PivotalSoftware");
        CompletableFuture<String> page2 = asyncTaskService.findUser("CloudFoundry");
        CompletableFuture<String> page3 = asyncTaskService.findUser("Spring-Projects");

        // Wait until they are all done
        //join() 的作用：让“主线程”等待“子线程”结束之后才能继续运行
        CompletableFuture.allOf(page1,page2,page3).join();

        // Print results, including elapsed time
        float exc = (float)(System.currentTimeMillis() - start)/1000;
        logger.info("Elapsed time: " + exc + " seconds");
        logger.info("--> " + page1.get());
        logger.info("--> " + page2.get());
        logger.info("--> " + page3.get());
    }
}
