package com.accounting.firm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 会计师事务所管理系统启动类
 */
@SpringBootApplication
@MapperScan("com.accounting.firm.**.mapper")
@EnableAsync
@EnableScheduling
public class AccountingFirmApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingFirmApplication.class, args);
    }
}
