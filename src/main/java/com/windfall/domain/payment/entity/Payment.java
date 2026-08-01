package com.windfall.domain.payment.entity;

import com.windfall.domain.payment.enums.PaymentStatus;
import com.windfall.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // 빌더용
@Builder
public class Payment extends BaseEntity {

  // 결제 객체 먼저 생성 후, 결제 api 호출한 다음 paymentKey 값 추가.
  @Column(nullable = true, unique = true)
  private String paymentKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status;

  @Embedded
  private PaymentSelection paymentSelection;

  @Column(nullable = false)
  private Long tradeId;

  @Column(nullable=false)
  private Long buyerId;

  @Column(nullable = false)
  private Long price;

  /** 결제 승인 완료 */
  public static Payment confirm(
      Long tradeId,
      String paymentKey,
      Long price,
      PaymentSelection selection
  ) {
    return Payment.builder()
        .tradeId(tradeId)
        .paymentKey(paymentKey)
        .price(price)
        .status(PaymentStatus.DONE)
        .paymentSelection(selection)
        .build();
  }

  /** 선점 시점: '결제 진행 중' 상태로 생성 */
  public static Payment request(
      Long tradeId, Long buyerId, String paymentKey, Long price, PaymentSelection selection) {
    return Payment.builder()
        .tradeId(tradeId)
        .buyerId(buyerId)
        .paymentKey(paymentKey)
        .price(price)
        .status(PaymentStatus.IN_PROGRESS)
        .paymentSelection(selection)
        .build();
  }

  public void changeStatus(PaymentStatus status) {
    this.status = status;
  }
}
