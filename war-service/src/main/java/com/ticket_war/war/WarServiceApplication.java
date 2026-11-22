package com.ticket_war.war;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WarServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WarServiceApplication.class, args);
	}

}
