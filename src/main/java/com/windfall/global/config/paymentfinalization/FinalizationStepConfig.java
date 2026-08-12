package com.windfall.global.config.paymentfinalization;

import com.windfall.api.payment.finalizationscheduler.FinalizationProcessor;
import com.windfall.api.payment.finalizationscheduler.FinalizationWriter;
import com.windfall.api.payment.finalizationscheduler.FinalizationReader;
import com.windfall.api.payment.finalizationscheduler.FinalizationCommand;
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
public class FinalizationStepConfig {
  private final FinalizationReader reader;
  private final FinalizationProcessor processor;
  private final FinalizationWriter writer;

  @Bean
  public Step finaliztionStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager) {

    return new StepBuilder("finalizationStep", jobRepository)
        .<Trade, FinalizationCommand>chunk(10, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .build();
  }
}
