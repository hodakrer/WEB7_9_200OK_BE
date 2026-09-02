package com.windfall.api.payment.service;

import static com.windfall.global.exception.ErrorCode.PAYMENT_REQUEST_LATE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.windfall.api.payment.dto.request.PaymentConfirmRequest;
import com.windfall.api.payment.dto.request.TossPaymentConfirmRequest;
import com.windfall.api.payment.dto.response.PaymentConfirmResponse;
import com.windfall.api.payment.dto.response.TossPaymentConfirmResponse;
import com.windfall.api.payment.service.retry.backoff.BackoffStrategy;
import com.windfall.api.payment.service.retry.backoff.ExponentialFullJitterBackoffStrategy;
import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.auction.repository.AuctionRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.domain.user.repository.UserRepository;
import com.windfall.global.exception.ErrorCode;
import com.windfall.global.exception.ErrorException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final WebClient webClient;
  private final AuctionRepository auctionRepository;
  private final TradeRepository tradeRepository;
  private final UserRepository userRepository;
  private final PaymentPreProcessService paymentPreProcessService;
  private final PaymentPostProcessService paymentPostProcessService;
  private final PaymentResponseValidator paymentResponseValidator;

  @Value("${spring.toss.secretkey}")
  private String widgetSecretKey;

  public PaymentConfirmResponse confirmPayment(
      PaymentConfirmRequest paymentConfirmRequest, Long buyerId) {

    // DTO에서 데이터 추출하며 예외처리.
    String paymentKey = paymentConfirmRequest.paymentKey();
    String orderId = paymentConfirmRequest.orderId();
    Long amount = paymentConfirmRequest.amount();
    Long auctionId = paymentConfirmRequest.auctionId();
    Auction auction = auctionRepository.findById(auctionId)
        .orElseThrow(() -> new ErrorException(ErrorCode.NOT_FOUND_AUCTION));

    // 올바른 유저인지 확인과 예외처리.
    Long sellerId = auction.getSeller().getId();
    if (!userRepository.existsById(sellerId)) {
      throw new ErrorException(ErrorCode.NOT_FOUND_SELLER);
    }
    if (!userRepository.existsById(buyerId)) {
      throw new ErrorException(ErrorCode.NOT_FOUND_BUYER);
    }

    Trade trade = paymentPreProcessService.acquirePaymentRequestPermission(auction, buyerId, amount, paymentKey);
    // 테스트용
    log.info(
        "Payment request claim success. auctionId={}, buyerId={}, tradeId={}",
        auctionId,
        buyerId,
        trade.getId()
    );

    // Toss PG사에서 요구하는 암호화
    Base64.Encoder encoder = Base64.getEncoder();
    byte[] encodedBytes = encoder.encode((widgetSecretKey + ":").getBytes(StandardCharsets.UTF_8));
    String authorization = "Basic " + new String(encodedBytes);

    // PG사 결제 승인 요청.
    BackoffStrategy exponentialFullJitterBackoffStrategy
        = new ExponentialFullJitterBackoffStrategy(100,2.0,3000);

    TossPaymentConfirmRequest tossRequest = new TossPaymentConfirmRequest(paymentKey, orderId,
        amount);

    TossPaymentConfirmResponse tossResponse
        = confirm(authorization, tossRequest, trade,
        exponentialFullJitterBackoffStrategy, paymentKey);

    // PG사 응답값 올바른지 확인.
    paymentResponseValidator.validate(tossResponse, tossRequest);

    // db에 결과값 저장.
    paymentPostProcessService.updateDatabaseAfterPayment(auctionId, trade, paymentKey, amount);

    return new PaymentConfirmResponse(
        tossResponse.orderId(), tossResponse.paymentKey(), tossResponse.totalAmount());
  }

  // toss api proceed해도 되는지 검증용 함수
  public void validatePaymentRequest(Long tradeBuyerId, TradeStatus status, Long requestBuyerId) {

    boolean isSameBuyer = tradeBuyerId.equals(requestBuyerId);
    boolean isRetryable =
        status == TradeStatus.PAYMENT_CANCELED
            || status == TradeStatus.PAYMENT_FAILED;

    if (isSameBuyer) {
      if (status != TradeStatus.PROCESSING) {
        throw new ErrorException(ErrorCode.INVALID_TRADE_INIT);
      }
    } else {
      if (!isRetryable) {
        throw new ErrorException(PAYMENT_REQUEST_LATE);
      }
    }
  }


  ////////// 결제 승인 요청을 위한 것들 ////////////
  ///
  /// 1. http 응답값
  private static final Set<String> RETRYABLE = Set.of(
      "PROVIDER_ERROR", "CARD_PROCESSING_ERROR",
      "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING",
      "FAILED_INTERNAL_SYSTEM_PROCESSING", "UNKNOWN_PAYMENT_ERROR",
      "IDEMPOTENT_REQUEST_PROCESSING");

  private static final Set<String> CUSTOMER_FAILED = Set.of(
      "INVALID_REJECT_CARD", "INVALID_STOPPED_CARD", "INVALID_CARD_LOST_OR_STOLEN",
      "INVALID_CARD_NUMBER", "INVALID_CARD_EXPIRATION", "INVALID_PASSWORD",
      "EXCEED_MAX_DAILY_PAYMENT_COUNT", "EXCEED_MAX_PAYMENT_AMOUNT",
      "EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT",
      "REJECT_ACCOUNT_PAYMENT", "REJECT_CARD_PAYMENT", "REJECT_CARD_COMPANY");

  private static final Set<String> CONFIG_FAILED = Set.of(
      "INVALID_API_KEY", "UNAUTHORIZED_KEY", "INCORRECT_BASIC_AUTH_FORMAT",
      "INVALID_UNREGISTERED_SUBMALL", "NOT_REGISTERED_BUSINESS", "NOT_FOUND_TERMINAL_ID");

  ///
  /// 2. 함수
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record TossError(String code, String message) {}

  TossPaymentConfirmResponse confirm(
      String authorization,
      TossPaymentConfirmRequest tossRequest,
      Trade trade,
      BackoffStrategy backoffStrategy,
      String idempotencyKey){

    int maxAttempts = 5;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return requestToToss(authorization, tossRequest,idempotencyKey);
      } catch (ErrorException e) {
        ErrorCode code = e.getErrorCode();

        // a. 일시적인 에러 → 재시도
        if (code == ErrorCode.PAYMENT_PG_TEMPORARY_ERROR) {
          // a-1. 마지막 시도였다면 UNKNOWN. PROCESSING인 채로 f놔둠.
          if (attempt == maxAttempts) {
            throw e;
          }
          // a-2. 횟수 남았으면 재시도
          long delay = backoffStrategy.nextDelay(attempt);
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
          }

          // b. 실패가 확실한 에러 → FAILED 확정
        } else if (code == ErrorCode.PAYMENT_CARD_REJECTED
            || code == ErrorCode.PAYMENT_PG_CONFIG_ERROR) {
          tradeRepository.updateStatus(trade.getId(), TradeStatus.PAYMENT_FAILED);
          throw e;

          // c. 모르는 에러 → 상태 확정 금지. retry 멈추고 PROCESSING인 채로 놔둠.
        } else {
          throw e;
        }
      }
    }

    // 모두 실패하면
    throw new ErrorException(ErrorCode.PAYMENT_UNKNOWN_PG_ERROR);
  }

  private TossPaymentConfirmResponse requestToToss(
      String authorization, TossPaymentConfirmRequest tossRequest, String idempotencyKey) {
    long start = System.nanoTime();
    try {
      return webClient.post()
          .uri("/v1/payments/confirm")
          .header(HttpHeaders.AUTHORIZATION, authorization)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
          .header("Idempotency-Key", idempotencyKey)
          .bodyValue(tossRequest)
          .retrieve()
          .onStatus(HttpStatusCode::isError, response ->
              response.bodyToMono(TossError.class)
                  .defaultIfEmpty(new TossError(null, null))
                  .map(body -> new ErrorException(classify(body.code()))))
          .bodyToMono(TossPaymentConfirmResponse.class)
          .block();
    } finally {
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;
      log.info("TOSS_CALL_ELAPSED = {}ms", elapsedMs);
    }
  }

  private ErrorCode classify(String tossCode) {
    if (tossCode == null)                        return ErrorCode.PAYMENT_UNKNOWN_PG_ERROR;
    if (RETRYABLE.contains(tossCode))            return ErrorCode.PAYMENT_PG_TEMPORARY_ERROR;
    if (CUSTOMER_FAILED.contains(tossCode))      return ErrorCode.PAYMENT_CARD_REJECTED;
    if (CONFIG_FAILED.contains(tossCode))        return ErrorCode.PAYMENT_PG_CONFIG_ERROR;
    if (tossCode.equals("ALREADY_PROCESSED_PAYMENT")) return ErrorCode.PAYMENT_ALREADY_PROCESSED;
    return ErrorCode.PAYMENT_UNKNOWN_PG_ERROR;   // 모르는 건 모른다고 남김.
  }
}
