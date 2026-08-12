package com.windfall.global.config;


import org.springframework.batch.core.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class BatchTestConfig {

  @Bean
  public JobLauncherTestUtils jobLauncherTestUtils(
      Job finalizationJob, JobLauncher jobLauncher) {

    JobLauncherTestUtils utils = new JobLauncherTestUtils();
    utils.setJob(finalizationJob);
    utils.setJobLauncher(jobLauncher);
    return utils;
  }
}