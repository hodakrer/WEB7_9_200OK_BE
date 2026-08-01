package com.windfall.api.payment.reconcilebatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windfall.api.auction.dto.request.AuctionCreateRequest;
import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.auction.enums.AuctionCategory;
import com.windfall.domain.auction.repository.AuctionRepository;
import com.windfall.domain.payment.entity.Payment;
import com.windfall.domain.payment.entity.PaymentSelection;
import com.windfall.domain.payment.enums.PaymentMethod;
import com.windfall.domain.payment.enums.PaymentProvider;
import com.windfall.domain.payment.enums.PaymentStatus;
import com.windfall.domain.payment.repository.PaymentRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.domain.user.entity.User;
import com.windfall.domain.user.enums.ProviderType;
import com.windfall.domain.user.repository.UserRepository;
import com.windfall.global.config.BatchTestConfig;
import com.windfall.global.config.MockTossConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({MockTossConfig.class, BatchTestConfig.class})
@TestPropertySource(properties = "payment.reconcile.fixed-delay=99999999")
class PaymentReconcileJobTest {

  @Autowired JobLauncherTestUtils jobLauncherTestUtils;
  @Autowired MockWebServer mockTossServer;
  @Autowired TradeRepository tradeRepository;
  @Autowired PaymentRepository paymentRepository;
  @Autowired AuctionRepository auctionRepository;
  @Autowired UserRepository userRepository;

  private static final Long BUYER_ID = 2L;

  private Auction auction;
  private int tossCallsBefore;

  @BeforeEach
  void setUp() {
    paymentRepository.deleteAll();
    tradeRepository.deleteAll();

    // 큐에 남은 이전 테스트의 응답 제거
    mockTossServer.setDispatcher(new QueueDispatcher());

    // providerId가 UNIQUE라면 매번 같은 값은 충돌 → 랜덤
    User seller = userRepository.save(new User(
        ProviderType.KAKAO, UUID.randomUUID().toString(), "email", "nickname", "imgUrl"));

    AuctionCreateRequest request = new AuctionCreateRequest("title", "description",
        AuctionCategory.CLOTHING, new ArrayList<>(), new ArrayList<>(),
        1L, 1L, 1L, LocalDateTime.now());
    auction = auctionRepository.save(Auction.create(request, seller));
    tossCallsBefore = mockTossServer.getRequestCount();
  }

  private int tossCallsInThisTest() {
    return mockTossServer.getRequestCount() - tossCallsBefore;
  }

  // ---------- 시나리오 ----------

  // ---------- 1. 상태변경 제대로 되나? -----------
  @Test
  void when_toss_is_in_progress_then_do_not_change_status() throws Exception {
    Long tradeId = givenProcessingTradeWithPayment("pk-1");
    enqueueToss("{\"paymentKey\":\"pk-1\",\"status\":\"IN_PROGRESS\"}");

    StepExecution step = launchAndGetStep();

    assertCounts(step, 1, 1, 0);
    assertEquals(TradeStatus.PROCESSING, findTrade(tradeId).getStatus());
    assertEquals(PaymentStatus.IN_PROGRESS, findPayment("pk-1").getStatus());
  }

  @Test
  void when_toss_is_done_then_mark_completed() throws Exception {
    Long tradeId = givenProcessingTradeWithPayment("pk-2");
    enqueueToss("{\"paymentKey\":\"pk-2\",\"status\":\"DONE\"}");

    StepExecution step = launchAndGetStep();

    assertCounts(step, 1, 0, 1);
    assertEquals(TradeStatus.PAYMENT_COMPLETED, findTrade(tradeId).getStatus());
    assertEquals(PaymentStatus.DONE, findPayment("pk-2").getStatus());
  }

  @Test
  void when_toss_is_aborted_then_mark_failed() throws Exception {
    Long tradeId = givenProcessingTradeWithPayment("pk-3");
    enqueueToss("{\"paymentKey\":\"pk-3\",\"status\":\"ABORTED\"}");

    StepExecution step = launchAndGetStep();

    assertCounts(step, 1, 0, 1);
    assertEquals(TradeStatus.PAYMENT_FAILED, findTrade(tradeId).getStatus());
    assertEquals(PaymentStatus.FAILED, findPayment("pk-3").getStatus());
  }

  // ---------- 2. 혹시 중간에 외부 통신 실패해도 나머지를 처리할 내구성이 있나? -----------
  @Test
  void when_pg_returns_error_then_batch_survives_and_status_unchanged() throws Exception {
    Long tradeId = givenProcessingTradeWithPayment("pk-4");
    mockTossServer.enqueue(new MockResponse().setResponseCode(500));

    JobExecution execution = launchJob();
    StepExecution step = firstStep(execution);

    assertEquals(BatchStatus.COMPLETED, execution.getStatus());  // 배치가 죽지 않음
    assertCounts(step, 1, 1, 0);
    assertEquals(TradeStatus.PROCESSING, findTrade(tradeId).getStatus());
    assertEquals(PaymentStatus.IN_PROGRESS, findPayment("pk-4").getStatus());
  }

  // ---------- 2. paymentKey를 위해 payment 객체 부르는데에 실패해도 나머지를 처리할 내구서이 있나? -----------
  @Test
  void when_payment_not_exists_then_pass() throws Exception {
    Long tradeId = givenProcessingTrade();   // payment 저장 안 함

    StepExecution step = launchAndGetStep();

    assertCounts(step, 1, 1, 0);
    assertEquals(0, tossCallsInThisTest());   // payment 없으면 토스 호출조차 없음
    assertEquals(TradeStatus.PROCESSING, findTrade(tradeId).getStatus());
  }

  // ---------- 3. 멱등성을 보장하나? -----------
  @Test
  void when_run_twice_then_second_run_writes_nothing() throws Exception {
    Long tradeId = givenProcessingTradeWithPayment("pk-6");
    enqueueToss("{\"paymentKey\":\"pk-6\",\"status\":\"DONE\"}");

    StepExecution first = launchAndGetStep();
    assertCounts(first, 1, 0, 1);
    assertEquals(TradeStatus.PAYMENT_COMPLETED, findTrade(tradeId).getStatus());

    StepExecution second = launchAndGetStep();   // 응답 추가 enqueue 없음

    assertCounts(second, 0, 0, 0);
    assertEquals(1, tossCallsInThisTest());   // 2회차엔 재조회 없음
    assertEquals(TradeStatus.PAYMENT_COMPLETED, findTrade(tradeId).getStatus());
    assertEquals(PaymentStatus.DONE, findPayment("pk-6").getStatus());
  }

  // ---------- helper ----------

  private Long givenProcessingTrade() {
    Trade trade = tradeRepository.save(Trade.builder()
        .auction(auction)
        .buyerId(BUYER_ID)
        .sellerId(auction.getSeller().getId())
        .finalPrice(1000L)
        .status(TradeStatus.PROCESSING)
        .build());
    return trade.getId();
  }

  private Long givenProcessingTradeWithPayment(String paymentKey) {
    Long tradeId = givenProcessingTrade();
    paymentRepository.save(Payment.request(
        tradeId, BUYER_ID, paymentKey, 1000L,
        new PaymentSelection(PaymentProvider.TOSS, PaymentMethod.MOBILE_PAYMENT)));
    return tradeId;
  }

  private void enqueueToss(String json) {
    mockTossServer.enqueue(new MockResponse()
        .setBody(json)
        .addHeader("Content-Type", "application/json"));
  }

  private JobExecution launchJob() throws Exception {
    return jobLauncherTestUtils.launchJob(new JobParametersBuilder()
        .addLong("runAt", System.nanoTime())   // 매 실행 고유값
        .toJobParameters());
  }

  private StepExecution launchAndGetStep() throws Exception {
    return firstStep(launchJob());
  }

  private StepExecution firstStep(JobExecution execution) {
    return execution.getStepExecutions().iterator().next();
  }

  /** read == filter + write (누락 0) 까지 함께 검증 */
  private void assertCounts(StepExecution step, long read, long filter, long write) {
    assertEquals(read, step.getReadCount());
    assertEquals(filter, step.getFilterCount());
    assertEquals(write, step.getWriteCount());
    assertEquals(step.getReadCount(), step.getFilterCount() + step.getWriteCount());
  }

  private Trade findTrade(Long id) {
    return tradeRepository.findById(id).orElseThrow();
  }

  private Payment findPayment(String paymentKey) {
    return paymentRepository.findByPaymentKey(paymentKey).orElseThrow();
  }
}