package manfred.bytedepth.adapter.web.config;

import manfred.bytedepth.adapter.web.util.PageViewInterceptor;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String imageDir;
    private final PageViewInterceptor pageViewInterceptor;

    public WebMvcConfig(@Value("${bytedepth.upload.image-dir}") String imageDir,
                        VisitRequestFilter visitRequestFilter,
                        ApplicationEventPublisher eventPublisher) {
        this.imageDir = imageDir;
        this.pageViewInterceptor = new PageViewInterceptor(visitRequestFilter, eventPublisher);
    }

    @Bean
    public PageViewInterceptor pageViewInterceptor() {
        return pageViewInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pageViewInterceptor)
                .addPathPatterns("/", "/about", "/posts", "/columns", "/columns/*",
                        "/projects", "/releases", "/search", "/user/*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path path = Paths.get(imageDir).toAbsolutePath();
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + path + "/");
    }
}
