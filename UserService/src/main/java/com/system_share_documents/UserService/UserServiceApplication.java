package com.system_share_documents.UserService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync  // Cần enable async để @Async hoạt động
@EnableScheduling  // Cần enable scheduling để @Scheduled hoạt động
@ComponentScan(basePackages = {
        "com.system_share_documents.UserService",
        // Chỉ scan các package cần thiết từ AppCommonService
        "com.system_share_documents.AppCommonService.enums",
        "com.system_share_documents.AppCommonService.event",
        "com.system_share_documents.AppCommonService.kafka.producer",
        "com.system_share_documents.AppCommonService.config.kafka",
        "com.system_share_documents.AppCommonService.config.thread",
        "com.system_share_documents.AppCommonService.utils",
        "com.system_share_documents.AppCommonService.constant"
})
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
