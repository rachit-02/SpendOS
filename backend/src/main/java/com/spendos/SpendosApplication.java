package com.spendos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot application entry point for SpendOS.
 * 
 * SpendOS is an intelligent personal financial operating system that transforms
 * authorized financial transaction data into understandable financial intelligence.
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.spendos.*.repository")
@ComponentScan(basePackages = "com.spendos")
public class SpendosApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpendosApplication.class, args);
    }

}
