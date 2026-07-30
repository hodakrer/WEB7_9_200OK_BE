package com.windfall.api.payment.service;

import static com.windfall.global.exception.ErrorCode.PAYMENT_REQUEST_LATE;

import com.windfall.domain.auction.entity.Auction;
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
          .status(TradeStatus.PROCESSING)
          .build();

      try {

        return tradeRepository.save(newTrade);

      } catch (DataIntegrityViolationException e) {
        log.warn("[RACE] UNIQUE constraint blocked duplicate trade. auctionId={}", auction.getId());
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
      log.warn("[RACE] Conditional UPDATE blocked re-entry. auctionId={}", auction.getId());
      throw new ErrorException(PAYMENT_REQUEST_LATE);
    }

    return tradeRepository.findByAuction(auction)
        .orElseThrow();
  }

}
