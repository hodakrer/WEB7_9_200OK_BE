package com.windfall.api.payment.finalizationscheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizationScheduler {

  private final JobLauncher jobLauncher;
  private final Job finalizationJob;

  // 이전 실행이 '끝난 뒤' 5분 후 다시 실행
  // @Scheduled(fixedDelay = 5 * 60 * 1000)
  @Scheduled(fixedDelayString = "${payment.finalize.fixed-delay:300000}")
  public void runFinalizationJob() {
    try {
      JobParameters jobParameters = new JobParametersBuilder()
          // 스프링 배치는 "Job 이름 + JobParameters 조합"이 이미 성공한 실행이면 재실행하지 않음.
          // → 매번 달라지는 현재 시각을 넣어 새 실행으로 인식시킴.
          .addLocalDateTime("runAt", LocalDateTime.now())
          .toJobParameters();

      jobLauncher.run(finalizationJob, jobParameters);
    } catch (Exception e) {
      log.error("결제 상태 확정 배치 실행 실패", e);
    }
  }

}
