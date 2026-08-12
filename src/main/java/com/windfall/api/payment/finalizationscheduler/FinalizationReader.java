package com.windfall.api.payment.finalizationscheduler;

import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.stereotype.Component;

@Component
public class FinalizationReader extends JpaCursorItemReader<Trade> {

  public FinalizationReader(EntityManagerFactory entityManagerFactory) {
    setName("processingTradeReader");
    setEntityManagerFactory(entityManagerFactory);
    setQueryString("""
        SELECT t FROM Trade t
        WHERE t.status = :status
        ORDER BY t.id ASC
        """);
    setParameterValues(Map.of("status", TradeStatus.PROCESSING));
  }
}
