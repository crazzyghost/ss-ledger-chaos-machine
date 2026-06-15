package com.softspark.chaos.config;

import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the chaos machine.
 * <p>
 * Configures virtual threads for async request handling (Java 21+).
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

  @Override
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setTaskExecutor(asyncTaskExecutor());
  }

  @Bean
  public AsyncTaskExecutor asyncTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }
}
