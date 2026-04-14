package com.windfall.api.payment.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.windfall.domain.auction.repository.AuctionRepository;
import com.windfall.domain.trade.enums.TradeStatus;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.domain.user.repository.UserRepository;
import com.windfall.global.exception.ErrorCode;
import com.windfall.global.exception.ErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceValidatePaymentRequestTest {
  @Mock
  WebClient webClient;
  @Mock
  AuctionRepository auctionRepository;
  @Mock
  TradeRepository tradeRepository;
  @Mock
  UserRepository userRepository;
  @Mock PaymentPostProcessService paymentPostProcessService;

  @InjectMocks
  PaymentService service;

  // 1. 본인 + COMPLETED → INVALID_TRADE_INIT
  @Test
  void throws_invalid_trade_init_when_same_buyer_and_not_pending() {
    ErrorException ex = assertThrows(ErrorException.class, () ->
        service.validatePaymentRequest(1L, TradeStatus.PAYMENT_COMPLETED, 1L)
    );

    assertEquals(ErrorCode.INVALID_TRADE_INIT, ex.getErrorCode());
  }

  // 2. 타인 + FAILED → 통과
  @Test
  void does_not_throw_when_other_buyer_and_status_is_failed() {
    assertDoesNotThrow(() ->
        service.validatePaymentRequest(1L, TradeStatus.PAYMENT_FAILED, 2L)
    );
  }

  // 3. 타인 + CANCELED → 통과
  @Test
  void does_not_throw_when_other_buyer_and_status_is_canceled() {
    assertDoesNotThrow(() ->
        service.validatePaymentRequest(1L, TradeStatus.PAYMENT_CANCELED, 2L)
    );
  }

  // 4. 타인 + PENDING → PAYMENT_REQUEST_LATE
  @Test
  void throws_payment_request_late_when_other_buyer_and_status_is_not_retryable_pending() {
    ErrorException ex = assertThrows(ErrorException.class, () ->
        service.validatePaymentRequest(1L, TradeStatus.PENDING, 2L)
    );

    assertEquals(ErrorCode.PAYMENT_REQUEST_LATE, ex.getErrorCode());
  }

  // 5. 타인 + COMPLETED → PAYMENT_REQUEST_LATE
  @Test
  void throws_payment_request_late_when_other_buyer_and_status_is_not_retryable_completed() {
    ErrorException ex = assertThrows(ErrorException.class, () ->
        service.validatePaymentRequest(1L, TradeStatus.PAYMENT_COMPLETED, 2L)
    );

    assertEquals(ErrorCode.PAYMENT_REQUEST_LATE, ex.getErrorCode());
  }

  // 6. 타인 + PURCHASE_CONFIRMED → PAYMENT_REQUEST_LATE
  @Test
  void throws_payment_request_late_when_other_buyer_and_status_is_not_retryable_purchase_confirmed() {
    ErrorException ex = assertThrows(ErrorException.class, () ->
        service.validatePaymentRequest(1L, TradeStatus.PURCHASE_CONFIRMED, 2L)
    );

    assertEquals(ErrorCode.PAYMENT_REQUEST_LATE, ex.getErrorCode());
  }
}
