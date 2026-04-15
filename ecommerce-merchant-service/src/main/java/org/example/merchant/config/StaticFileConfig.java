package org.example.merchant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class StaticFileConfig implements WebMvcConfigurer {

    private final String merchantRootDir;

    public StaticFileConfig(@Value("${app.upload.merchant-root:./data/uploads/merchant}") String merchantRootDir) {
        this.merchantRootDir = merchantRootDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(merchantRootDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/files/merchant/**")
                .addResourceLocations(location);
    }
}

