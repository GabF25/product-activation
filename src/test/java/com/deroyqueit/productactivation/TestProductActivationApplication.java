package com.deroyqueit.productactivation;

import org.springframework.boot.SpringApplication;

public class TestProductActivationApplication {

	public static void main(String[] args) {
		SpringApplication.from(ProductActivationApplication::main)
				.with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("local")
				.run(args);
	}

}
