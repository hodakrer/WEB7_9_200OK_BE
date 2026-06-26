package com.windfall.api.payment.service;

import static com.windfall.global.exception.ErrorCode.PAYMENT_REQUEST_LATE;

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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final WebClient webClient;
  private final AuctionRepository auctionRepository;
  private final TradeRepository tradeRepository;
  private final UserRepository userRepository;
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

    Trade trade = acquirePaymentRequestPermission(auction, buyerId, amount);

    // toss api proceed해도 되는지 검증
    validatePaymentRequest(buyerId, trade.getStatus(), trade.getBuyerId());

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
        = confirm(authorization, tossRequest, trade, exponentialFullJitterBackoffStrategy);

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
      if (status != TradeStatus.PENDING) {
        throw new ErrorException(ErrorCode.INVALID_TRADE_INIT);
      }
    } else {
      if (!isRetryable) {
        throw new ErrorException(PAYMENT_REQUEST_LATE);
      }
    }
  }

  @Transactional
  public Trade acquirePaymentRequestPermission(Auction auction, Long buyerId, Long amount) {

    Trade existingTrade =
        tradeRepository.findByAuction(auction)
            .orElse(null);

    // 최초로 trade 생성/UNIQUE로 trade 다수 생성 예방.
    if (existingTrade == null) {

      Trade newTrade = Trade.builder()
          .auction(auction)
          .buyerId(buyerId)
          .sellerId(auction.getSeller().getId())
          .finalPrice(amount)
          .status(TradeStatus.PENDING)
          .build();

      try {

        return tradeRepository.save(newTrade);

      } catch (DataIntegrityViolationException e) {

        throw new ErrorException(PAYMENT_REQUEST_LATE);
      }
    }

    // 기존 trade가 결제 가능 상태라면, 결제 요청을 선점 시도(PROCESSING으로 상태 변경)
    int updated = tradeRepository.reservePaymentProcessing(
        auction,
        TradeStatus.PROCESSING,
        List.of(
            TradeStatus.PAYMENT_FAILED,
            TradeStatus.PAYMENT_CANCELED
        )
    );

    if (updated == 0) {
      throw new ErrorException(PAYMENT_REQUEST_LATE);
    }

    return tradeRepository.findByAuction(auction)
        .orElseThrow();
  }


  TossPaymentConfirmResponse confirm(
      String authorization,
      TossPaymentConfirmRequest tossRequest,
      Trade trade,
      BackoffStrategy backoffStrategy){

    int maxAttempts = 5;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {

      try {

        TossPaymentConfirmResponse tossResponse = webClient.post()
            .uri("/v1/payments/confirm")
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .bodyValue(tossRequest)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response ->
                Mono.error(new ErrorException(ErrorCode.PAYMENT_CONFIRM_FAILED))
            )
            .bodyToMono(TossPaymentConfirmResponse.class)
            .block();

        // 성공
        return tossResponse;

      } catch (ErrorException e) {

        // 마지막 시도라면 최종 실패
        if (attempt == maxAttempts) {
          trade.changeStatus(TradeStatus.PAYMENT_FAILED);
          throw e;
        }

        // Full Jitter 대기
        long delay = backoffStrategy.nextDelay(attempt);

        try {
          Thread.sleep(delay);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(ex);
        }
      }
    }

    throw new IllegalStateException("Retry loop terminated unexpectedly.");
  }
}
