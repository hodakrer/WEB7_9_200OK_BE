package com.windfall.api.payment.reconcilebatch;

import com.windfall.domain.payment.entity.Payment;
import com.windfall.domain.payment.enums.PaymentStatus;
import com.windfall.domain.payment.repository.PaymentRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

// TODO: 배치 성공 처리 후 auction 완료 처리, 채팅방 생성도!
@Slf4j
@Component
public class PaymentReconcileProcessor implements ItemProcessor<Trade, ReconcileCommand> {

  private final PaymentRepository paymentRepository;
  private final WebClient webClient;
  private final String authorization;

  public PaymentReconcileProcessor(
      PaymentRepository paymentRepository,
      WebClient webClient,
      @Value("${spring.toss.secretkey}") String widgetSecretKey) {
    this.paymentRepository = paymentRepository;
    this.webClient = webClient;
    this.authorization = "Basic " + Base64.getEncoder()
        .encodeToString((widgetSecretKey + ":").getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public ReconcileCommand process(Trade trade) {

    // 1. (tradeId, buyerId)로 가장 최근 payment 1건 조회
    Payment payment = paymentRepository
        .findTopByTradeIdAndBuyerIdOrderByIdDesc(trade.getId(), trade.getBuyerId())
        .orElse(null);

    if (payment == null || payment.getPaymentKey() == null) {
      return null; // 대사할 payment 없음 → 패스
    }

    // 2. paymentKey(멱등성키)로 토스에 결제 상태 조회
    TossPaymentInquiryResponse response;
    try {
      response = webClient.get()
          .uri("/v1/payments/{paymentKey}", payment.getPaymentKey())
          .header(HttpHeaders.AUTHORIZATION, authorization)
          .retrieve()
          .bodyToMono(TossPaymentInquiryResponse.class)
          .block();
    } catch (Exception e) {
      log.warn("토스 결제 조회 실패. tradeId={}, paymentKey={}",
          trade.getId(), payment.getPaymentKey(), e);
      return null; // 이번엔 패스 → 다음 배치 때 자동 재시도
    }

    if (response == null || response.status() == null) {
      return null;
    }

    // 3. 토스 상태 → 우리 상태 결정
    return switch (response.status()) {
      case "DONE" -> new ReconcileCommand(
          trade.getId(), TradeStatus.PAYMENT_COMPLETED,
          payment.getId(), PaymentStatus.DONE);

      case "ABORTED", "EXPIRED" -> new ReconcileCommand(
          trade.getId(), TradeStatus.PAYMENT_FAILED,
          payment.getId(), PaymentStatus.FAILED);

      default -> null; // READY, IN_PROGRESS 등 진행중 → 패스
    };
  }
}