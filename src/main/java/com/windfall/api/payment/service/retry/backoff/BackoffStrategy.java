package com.windfall.api.payment.service.retry.backoff;

public interface BackoffStrategy {
  /**
   * retryCount : 1부터 시작
   *
   * @return milliseconds
   */
  long nextDelay(int retryCount);

}
