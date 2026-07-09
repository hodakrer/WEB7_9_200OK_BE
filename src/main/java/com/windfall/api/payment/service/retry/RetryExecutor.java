package com.windfall.api.payment.service.retry;

import com.windfall.api.payment.service.retry.backoff.BackoffStrategy;
import java.util.Set;
import java.util.concurrent.Callable;

public class RetryExecutor {

  private final int maxAttempts;
  private final BackoffStrategy backoffStrategy;
  private final Set<Class<? extends Throwable>> retryableExceptions;

  public RetryExecutor(
      int maxAttempts,
      BackoffStrategy backoffStrategy,
      Set<Class<? extends Throwable>> retryableExceptions
  ) {
    this.maxAttempts = maxAttempts;
    this.backoffStrategy = backoffStrategy;
    this.retryableExceptions = retryableExceptions;
  }

  public <T> T execute(Callable<T> action) {

    int attempt = 1;

    while (true) {

      try {
        return action.call();
      }

      catch (Throwable e) {

        if (!isRetryable(e) || attempt >= maxAttempts) {

          if (e instanceof RuntimeException runtimeException) {
            throw runtimeException;
          }

          throw new RuntimeException(e);
        }

        sleep(attempt);

        attempt++;
      }
    }
  }

  private void sleep(int attempt) {

    long delay = backoffStrategy.nextDelay(attempt);

    try {
      Thread.sleep(delay);
    }

    catch (InterruptedException e) {

      Thread.currentThread().interrupt();

      throw new RuntimeException(e);
    }
  }

  private boolean isRetryable(Throwable throwable) {

    return retryableExceptions.stream()
        .anyMatch(type -> type.isAssignableFrom(
            throwable.getClass()));
  }
}
