package com.system_share_documents.WatermarkWorkerService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.system_share_documents")
@ConfigurationPropertiesScan
public class WatermarkWorkerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WatermarkWorkerServiceApplication.class, args);
	}

}
