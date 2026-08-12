package com.windfall.global.config.paymentfinalization;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// SpringBatch에서 Job과 Step은 @Bean 정의로 만듬. (클래스 상속 방식 X)
@Configuration
public class FinalizationJobConfig {
  @Bean
  public Job finalizationJob(JobRepository jobRepository, Step finalizationStep) {
    return new JobBuilder("finalizationJob", jobRepository)
        .start(finalizationStep)
        .build();
  }
}
