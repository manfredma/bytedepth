package manfred.bytedepth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "manfred.bytedepth")
@org.springframework.scheduling.annotation.EnableScheduling
@EnableAsync
public class BytedepthApplication {
    public static void main(String[] args) {
        SpringApplication.run(BytedepthApplication.class, args);
    }
}
