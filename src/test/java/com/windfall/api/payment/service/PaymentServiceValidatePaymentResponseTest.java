package com.windfall.api.payment.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.windfall.api.payment.dto.request.TossPaymentConfirmRequest;
import com.windfall.api.payment.dto.response.TossPaymentConfirmResponse;
import com.windfall.global.exception.ErrorCode;
import com.windfall.global.exception.ErrorException;
import org.junit.jupiter.api.Test;

public class PaymentServiceValidatePaymentResponseTest {


  PaymentResponseValidator validator = new PaymentResponseValidator();

  @Test
  void should_pass_when_response_is_valid() {
    // given
    TossPaymentConfirmResponse response =
        new TossPaymentConfirmResponse("Payment-1", "orderId-1", 1000L, "card", "DONE");

    TossPaymentConfirmRequest request =
        new TossPaymentConfirmRequest("Payment-1", "orderId-1", 1000L);

    // when & then
    assertDoesNotThrow(() ->
        validator.validate(response, request)
    );
  }

  @Test
  void should_throw_PAYMENT_ORDER_MISMATCH_when_orderId_differs() {
    // given
    TossPaymentConfirmResponse response =
        new TossPaymentConfirmResponse("Payment-1", "orderId-1", 1000L, "card", "DONE");

    TossPaymentConfirmRequest request =
        new TossPaymentConfirmRequest("Payment-1", "orderId-2", 1000L);

    // when & then
    assertThatThrownBy(() ->
        validator.validate(response, request)
    )
        .isInstanceOf(ErrorException.class)
        .extracting(e -> ((ErrorException)e).getErrorCode())
        .isEqualTo(ErrorCode.PAYMENT_ORDER_MISMATCH);
  }

  @Test
  void should_throw_PAYMENT_AMOUNT_MISMATCH_when_amount_differs() {
    // given
    TossPaymentConfirmResponse response =
        new TossPaymentConfirmResponse("Payment-1", "orderId-1", 1000L, "card", "DONE");

    TossPaymentConfirmRequest request =
        new TossPaymentConfirmRequest("Payment-1", "orderId-1", 2000L);

    // when & then
    assertThatThrownBy(() ->
        validator.validate(response, request)
    )
        .isInstanceOf(ErrorException.class)
        .extracting(e -> ((ErrorException)e).getErrorCode())
        .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
  }

}
