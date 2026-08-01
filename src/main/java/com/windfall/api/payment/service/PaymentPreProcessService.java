package com.windfall.api.payment.service;

import static com.windfall.global.exception.ErrorCode.PAYMENT_REQUEST_LATE;

import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.payment.entity.Payment;
import com.windfall.domain.payment.entity.PaymentSelection;
import com.windfall.domain.payment.enums.PaymentMethod;
import com.windfall.domain.payment.enums.PaymentProvider;
import com.windfall.domain.payment.repository.PaymentRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.global.exception.ErrorException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPreProcessService {

  private final TradeRepository tradeRepository;
  private final PaymentRepository paymentRepository;

  @Transactional
  public Trade acquirePaymentRequestPermission(Auction auction, Long buyerId, Long amount, String paymentKey) {

    Trade existingTrade =
        tradeRepository.findByAuction(auction)
            .orElse(null);

    // 경로 1: 최초 선점 (trade 생성). 중복 생성 요청은 UNIQUE로 예방.
    if (existingTrade == null) {

      Trade newTrade = Trade.builder()
          .auction(auction)
          .buyerId(buyerId)
          .sellerId(auction.getSeller().getId())
          .finalPrice(amount)
          .status(TradeStatus.PROCESSING)
          .build();

      Trade savedTrade;
      try {
        savedTrade = tradeRepository.save(newTrade);
      } catch (DataIntegrityViolationException e) {
        log.warn("[RACE] UNIQUE constraint blocked duplicate trade. auctionId={}", auction.getId());
        throw new ErrorException(PAYMENT_REQUEST_LATE);
      }

      savePaymentInProgress(savedTrade, buyerId, amount, paymentKey);
      return savedTrade;
    }

    // 경로 2: 기존 trade가 결제 가능 상태라면,
    // 결제 요청을 선점 시도(PROCESSING으로 상태 변경)
    // 중복 요청은 원자적 UPDATE문으로 예방.
    int updated = tradeRepository.reservePaymentProcessing(
        auction,
        buyerId,
        TradeStatus.PROCESSING,
        List.of(
            TradeStatus.PAYMENT_FAILED,
            TradeStatus.PAYMENT_CANCELED
        )
    );

    if (updated == 0) {
      log.warn("[RACE] Conditional UPDATE blocked re-entry. auctionId={}", auction.getId());
      throw new ErrorException(PAYMENT_REQUEST_LATE);
    }

    Trade trade = tradeRepository.findByAuction(auction).orElseThrow();
    savePaymentInProgress(trade, buyerId, amount, paymentKey);

    return tradeRepository.findByAuction(auction)
        .orElseThrow();
  }

  private void savePaymentInProgress(Trade trade, Long buyerId, Long amount, String paymentKey) {
    // TODO: PaymentMethod는 프론트에서 안 내려와 기존 코드처럼 임시 하드코딩 유지
    Payment payment = Payment.request(
        trade.getId(), buyerId, paymentKey, amount,
        new PaymentSelection(PaymentProvider.TOSS, PaymentMethod.MOBILE_PAYMENT));
    paymentRepository.save(payment);
  }
}
