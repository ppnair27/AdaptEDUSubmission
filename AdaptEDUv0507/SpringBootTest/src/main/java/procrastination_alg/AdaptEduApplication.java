package procrastination_alg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"procrastination_alg", "com.example.controller"})
public class AdaptEduApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdaptEduApplication.class, args);
    }

}