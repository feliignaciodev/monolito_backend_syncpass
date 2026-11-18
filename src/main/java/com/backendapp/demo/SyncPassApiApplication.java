package com.backendapp.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SyncPassApiApplication {
	public static void main(String[] args) {
		SpringApplication.run(SyncPassApiApplication.class, args);
	}
}
