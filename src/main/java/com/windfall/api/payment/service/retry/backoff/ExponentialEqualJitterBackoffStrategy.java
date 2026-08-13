package com.windfall.api.payment.service.retry.backoff;

import java.util.concurrent.ThreadLocalRandom;

public class ExponentialEqualJitterBackoffStrategy implements BackoffStrategy{

  private final long initialInterval;
  private final double multiplier;
  private final long maxInterval;

  public ExponentialEqualJitterBackoffStrategy(
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

    long half = capped / 2;

    return half + ThreadLocalRandom.current()
        .nextLong(capped - half + 1);
  }
}
