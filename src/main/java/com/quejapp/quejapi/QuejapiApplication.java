package com.quejapp.quejapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
@EnableCaching
public class QuejapiApplication {

	public static void main(String[] args) { SecurityContextHolder.setStrategyName(
			SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
		SpringApplication.run(QuejapiApplication.class, args);
	}

}
