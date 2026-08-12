package com.windfall.api.payment.finalizationscheduler;

import com.windfall.domain.payment.repository.PaymentRepository;
import com.windfall.domain.trade.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizationWriter implements ItemWriter<FinalizationCommand> {

  private final TradeRepository tradeRepository;
  private final PaymentRepository paymentRepository;

  @Override
  public void write(Chunk<? extends FinalizationCommand> chunk) {
    for (FinalizationCommand command : chunk) {
      tradeRepository.updateStatus(command.tradeId(), command.tradeStatus());
      paymentRepository.updateStatus(command.paymentId(), command.paymentStatus());
    }
  }
}