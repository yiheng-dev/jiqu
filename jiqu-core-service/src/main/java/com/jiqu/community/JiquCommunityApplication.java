package com.jiqu.community;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.jiqu.community.mapper")
@SpringBootApplication
public class JiquCommunityApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiquCommunityApplication.class, args);
    }

}
