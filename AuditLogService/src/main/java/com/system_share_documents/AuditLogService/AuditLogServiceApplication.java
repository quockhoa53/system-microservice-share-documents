package com.system_share_documents.AuditLogService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.system_share_documents")
@ConfigurationPropertiesScan
@EnableAsync
public class AuditLogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditLogServiceApplication.class, args);
	}

}
