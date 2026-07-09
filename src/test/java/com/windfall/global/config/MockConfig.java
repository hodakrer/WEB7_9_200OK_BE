package com.windfall.global.config;

import com.windfall.api.payment.service.PaymentResponseValidator;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class MockConfig {

  @Bean
  public PaymentResponseValidator paymentResponseValidator() {
    return Mockito.mock(PaymentResponseValidator.class);
  }
}