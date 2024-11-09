package com.example.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Bean;

/*
 * Create Spring Boot Application and set a default controller
 */

@SpringBootApplication
public class MainApplication {
  public static void main(final String[] args) {
    SpringApplication.run(MainApplication.class, args);
  }

  // with @Bean, whatever is returned will be injected into the context
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
