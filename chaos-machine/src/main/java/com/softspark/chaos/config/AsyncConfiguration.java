package com.softspark.chaos.config;

import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Async execution configuration.
 *
 * <p>Backs Spring's {@code @Async} machinery with a virtual-thread executor (Java 21+) so that
 * every asynchronous task runs on a lightweight virtual thread instead of a pooled platform
 * thread.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

  /**
   * Creates an {@link AsyncTaskExecutor} backed by {@link Executors#newVirtualThreadPerTaskExecutor()}.
   *
   * <p>Each submitted task receives its own virtual thread, eliminating thread-pool sizing concerns
   * for I/O-bound workloads.
   *
   * @return the virtual-thread task executor
   */
  @Bean
  public AsyncTaskExecutor applicationTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }
}
