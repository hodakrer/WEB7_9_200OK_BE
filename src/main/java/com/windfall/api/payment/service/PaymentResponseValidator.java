package com.windfall.api.payment.service;

import com.windfall.api.payment.dto.request.TossPaymentConfirmRequest;
import com.windfall.api.payment.dto.response.TossPaymentConfirmResponse;
import com.windfall.global.exception.ErrorCode;
import com.windfall.global.exception.ErrorException;

public class PaymentResponseValidator {

  public void validate(TossPaymentConfirmResponse response,
      TossPaymentConfirmRequest request) {

    if (!response.orderId().equals(request.orderId())) {
      throw new ErrorException(ErrorCode.PAYMENT_ORDER_MISMATCH);
    }

    if (!response.totalAmount().equals(request.amount())) {
      throw new ErrorException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }
  }
}
