package com.windfall.api.payment.finalizationscheduler;

import com.windfall.domain.payment.enums.PaymentStatus;
import com.windfall.domain.trade.enums.TradeStatus;

public record FinalizationCommand(
    Long tradeId,
    TradeStatus tradeStatus,
    Long paymentId,
    PaymentStatus paymentStatus
) {
}
