package com.deroyqueit.productactivation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProductActivationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductActivationApplication.class, args);
    }
}
