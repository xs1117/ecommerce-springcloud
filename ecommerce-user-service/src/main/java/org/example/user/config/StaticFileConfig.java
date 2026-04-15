package org.example.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class StaticFileConfig implements WebMvcConfigurer {

    private final String userRootDir;

    public StaticFileConfig(@Value("${app.upload.user-root:./data/uploads/user}") String userRootDir) {
        this.userRootDir = userRootDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(userRootDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/files/user/**")
                .addResourceLocations(location);
    }
}

