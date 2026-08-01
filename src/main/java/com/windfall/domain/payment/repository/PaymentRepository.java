package com.windfall.domain.payment.repository;

import com.windfall.domain.payment.entity.Payment;
import com.windfall.domain.payment.enums.PaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByPaymentKey(String paymentKey);

  // OrderByIdDesc: 가장 최근 Trade 결제 1건만 잡기 위함.
  Optional<Payment> findTopByTradeIdAndBuyerIdOrderByIdDesc(Long tradeId, Long buyerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE Payment p SET p.status = :status WHERE p.id = :id")
  int updateStatus(@Param("id") Long id, @Param("status") PaymentStatus status);
}
