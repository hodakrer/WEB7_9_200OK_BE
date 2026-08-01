package com.windfall.global.config.paymentreconcile;

import com.windfall.api.payment.reconcilebatch.PaymentReconcileProcessor;
import com.windfall.api.payment.reconcilebatch.PaymentReconcileWriter;
import com.windfall.api.payment.reconcilebatch.ProcessingTradeReader;
import com.windfall.api.payment.reconcilebatch.ReconcileCommand;
import com.windfall.domain.trade.entity.Trade;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class PaymentReconcileStepConfig {
  private final ProcessingTradeReader reader;
  private final PaymentReconcileProcessor processor;
  private final PaymentReconcileWriter writer;

  @Bean
  public Step paymentReconcileStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager) {

    return new StepBuilder("paymentReconcileStep", jobRepository)
        .<Trade, ReconcileCommand>chunk(10, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
  }
}
