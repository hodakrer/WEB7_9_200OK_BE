package com.windfall.api.payment.reconcilebatch;

import com.windfall.domain.payment.enums.PaymentStatus;
import com.windfall.domain.trade.enums.TradeStatus;

public record ReconcileCommand(
    Long tradeId,
    TradeStatus tradeStatus,
    Long paymentId,
    PaymentStatus paymentStatus
) {
}
