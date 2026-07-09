package com.windfall.global.config;

import com.windfall.api.payment.service.PaymentPostProcessService;
import com.windfall.api.payment.service.PaymentResponseValidator;
import com.windfall.api.payment.service.PaymentService;
import com.windfall.domain.auction.repository.AuctionRepository;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.domain.user.repository.UserRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class PaymentServiceTestConfig {

  @Bean
  @Primary
  public PaymentService paymentService(
      WebClient webClient,
      AuctionRepository auctionRepository,
      TradeRepository tradeRepository,
      UserRepository userRepository,
      PaymentPostProcessService paymentPostProcessService,
      PaymentResponseValidator paymentResponseValidator
  ) {
    PaymentService real = new PaymentService(
        webClient,
        auctionRepository,
        tradeRepository,
        userRepository,
        paymentPostProcessService,
        paymentResponseValidator
    );

    return Mockito.spy(real);
  }
}