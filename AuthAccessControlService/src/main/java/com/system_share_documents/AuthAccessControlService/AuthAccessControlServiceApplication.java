package com.system_share_documents.AuthAccessControlService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.system_share_documents")
@ConfigurationPropertiesScan
@EnableAsync
public class AuthAccessControlServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthAccessControlServiceApplication.class, args);
	}

}
