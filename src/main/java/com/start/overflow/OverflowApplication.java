package com.start.overflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OverflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(OverflowApplication.class, args);
	}

}
