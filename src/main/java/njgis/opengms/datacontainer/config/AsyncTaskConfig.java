package njgis.opengms.datacontainer.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @Author mingyuan
 * @Date 2020.07.13 15:33
 */
@Configuration//告知spring这是一个配置类
@EnableAsync
//配置类实现接口AsyncConfigurator，返回一个ThreadPoolTaskExecutor线程池对象
public class AsyncTaskConfig implements AsyncConfigurer {
    @Override
    @Bean
    public Executor getAsyncExecutor(){
        ThreadPoolTaskExecutor threadPool = new ThreadPoolTaskExecutor();
        //核心线程数
        threadPool.setCorePoolSize(10);
        //最大线程数
        threadPool.setMaxPoolSize(100);
        //线程池所使用的缓冲队列
        threadPool.setQueueCapacity(10);
        //等待任务在关机时完成--表明等待所有线程执行完
        threadPool.setWaitForTasksToCompleteOnShutdown(true);
        //等待时间 （默认为0，此时立即停止）， 并没等待**秒后强制停止
        threadPool.setAwaitTerminationSeconds(60);
        //线程名称前缀
        threadPool.setThreadNamePrefix("Data-Container-");
        //初始化线程
        threadPool.initialize();
        return threadPool;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler(){
        return null;
    }
}
