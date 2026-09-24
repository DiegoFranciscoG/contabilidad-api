package io.github.diegofranciscog.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ContabilidadApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContabilidadApiApplication.class, args);
    }
}
