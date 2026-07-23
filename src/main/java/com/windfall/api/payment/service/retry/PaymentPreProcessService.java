package com.windfall.api.payment.service.retry;

import static com.windfall.global.exception.ErrorCode.PAYMENT_REQUEST_LATE;

import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.global.exception.ErrorCode;
import com.windfall.global.exception.ErrorException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentPreProcessService {

  private final TradeRepository tradeRepository;

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

}
