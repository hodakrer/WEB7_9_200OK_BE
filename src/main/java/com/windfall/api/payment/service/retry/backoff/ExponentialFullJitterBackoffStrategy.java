// 아래 링크를 참고해서 FULL JITTER를 선택하였습니다.
// https://aws.amazon.com/ko/blogs/architecture/exponential-backoff-and-jitter/

package com.windfall.api.payment.service.retry.backoff;

import java.util.concurrent.ThreadLocalRandom;

public class ExponentialFullJitterBackoffStrategy implements BackoffStrategy {

  private final long initialInterval;
  private final double multiplier;
  private final long maxInterval;

  public ExponentialFullJitterBackoffStrategy(
      long initialInterval,
      double multiplier,
      long maxInterval
  ) {
    if (initialInterval <= 0) {
      throw new IllegalArgumentException("initialInterval must be positive.");
    }

    if (multiplier < 1.0) {
      throw new IllegalArgumentException("multiplier must be >= 1.0");
    }

    if (maxInterval < initialInterval) {
      throw new IllegalArgumentException(
          "maxInterval must be >= initialInterval");
    }

    this.initialInterval = initialInterval;
    this.multiplier = multiplier;
    this.maxInterval = maxInterval;
  }

  @Override
  public long nextDelay(int retryCount) {

    if (retryCount <= 0) {
      throw new IllegalArgumentException(
          "retryCount must start from 1.");
    }

    double exponential =
        initialInterval * Math.pow(multiplier, retryCount - 1);

    long capped =
        Math.min((long) exponential, maxInterval);

    return ThreadLocalRandom.current()
        .nextLong(capped + 1);
  }
}
