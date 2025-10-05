package com.system_share_documents.CloudConfigServerService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class CloudConfigServerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CloudConfigServerServiceApplication.class, args);
	}

}
