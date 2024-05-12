package com.bootcamp_proj.bootcampproj;

import com.bootcamp_proj.bootcampproj.standalone_services.BrtHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;

@SpringBootApplication
@ComponentScan(basePackages = {"com.bootcamp_proj"})
public class BootcampProjApplication {
	public static void main(String[] args) {
		SpringApplication.run(BootcampProjApplication.class, args);
//		BrtHandler brt = BrtHandler.getInstance();
//		brt.startWithExistingFile();
	}
}