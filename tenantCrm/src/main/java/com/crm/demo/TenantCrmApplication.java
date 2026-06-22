package com.crm.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TenantCrmApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenantCrmApplication.class, args);
	}

}

