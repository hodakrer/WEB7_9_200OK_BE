package com.windfall.domain.trade.repository;

import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, Long> {
  Optional<Trade> findByAuction(Auction auction);
  long countByAuction(Auction auction);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      UPDATE Trade t
      SET t.status = :processingStatus
      WHERE t.auction = :auction
        AND t.status IN :retryableStatuses
      """)
  int reservePaymentProcessing(
      @Param("auction") Auction auction,
      @Param("processingStatus") TradeStatus processingStatus,
      @Param("retryableStatuses") List<TradeStatus> retryableStatuses
  );
}
