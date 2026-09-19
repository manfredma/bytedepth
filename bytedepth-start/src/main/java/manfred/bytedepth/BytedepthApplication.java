package manfred.bytedepth;

import manfred.bytedepth.app.analytics.ArchiveViewLogsCmdExe;
import manfred.bytedepth.app.analytics.ViewLogArchivePort;
import manfred.bytedepth.app.analytics.ViewLogRetentionPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication(scanBasePackages = "manfred.bytedepth")
@ConfigurationPropertiesScan
@org.springframework.scheduling.annotation.EnableScheduling
@EnableAsync
public class BytedepthApplication {
    public static void main(String[] args) {
        SpringApplication.run(BytedepthApplication.class, args);
    }

    @Bean
    Clock analyticsClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }

    @Bean
    ViewLogRetentionPolicy viewLogRetentionPolicy(
            @Value("${bytedepth.analytics.retention-days:7}") int retentionDays) {
        return new ViewLogRetentionPolicy(retentionDays, ZoneId.of("Asia/Shanghai"));
    }

    @Bean
    @ConditionalOnBean(ViewLogArchivePort.class)
    ArchiveViewLogsCmdExe archiveViewLogsCmdExe(ViewLogArchivePort archivePort,
                                                ViewLogRetentionPolicy retentionPolicy) {
        return new ArchiveViewLogsCmdExe(archivePort, retentionPolicy);
    }

    @Bean(name = "viewLogArchiveScheduler")
    ThreadPoolTaskScheduler viewLogArchiveScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("view-log-archive-");
        return scheduler;
    }

    @Bean(name = "viewLogTablespaceScheduler")
    ThreadPoolTaskScheduler viewLogTablespaceScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("view-log-tablespace-");
        return scheduler;
    }
}
