package com.windfall.domain.trade.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TradeStatus {
  PAYMENT_CANCELED("결제 취소"),
  PAYMENT_COMPLETED("결제 완료"),
  PURCHASE_CONFIRMED("구매 확정"),
  PAYMENT_FAILED("결제 실패"),
  PROCESSING("결제 진행 중");

  private final String description;
}
