package com.codehows.daehobe.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${fileLocation}")
    private String fileLocation;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // "/file/**" 경로 요청이 들어오면, 실제 로컬 파일 시스템(fileLocation)에 매핑
        registry.addResourceHandler("/file/**")
                .addResourceLocations("file:///" + fileLocation + "/");

        // 예: C:/daeho-be/file/abc.jpg 파일이 있으면, 브라우저에서 http://localhost:8080/file/abc.jpg로 접근 가능
    }
}
