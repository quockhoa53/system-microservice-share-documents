package com.system_share_documents.AppCommonService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.system_share_documents.AppCommonService.config.properties")
public class AppCommonServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AppCommonServiceApplication.class, args);
	}

}
