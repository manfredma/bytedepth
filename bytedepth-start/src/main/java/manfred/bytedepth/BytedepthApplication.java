package manfred.bytedepth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "manfred.bytedepth")
@MapperScan("manfred.bytedepth.infrastructure")
public class BytedepthApplication {
    public static void main(String[] args) {
        SpringApplication.run(BytedepthApplication.class, args);
    }
}
