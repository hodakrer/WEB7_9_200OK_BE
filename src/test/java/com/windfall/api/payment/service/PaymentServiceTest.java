package com.windfall.api.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windfall.api.auction.service.AuctionStateService;
import com.windfall.api.payment.dto.request.PaymentConfirmRequest;
import com.windfall.api.payment.dto.response.PaymentConfirmResponse;
import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.auction.repository.AuctionRepository;
import com.windfall.domain.chat.repository.ChatRoomRepository;
import com.windfall.domain.payment.repository.PaymentRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.domain.user.entity.User;
import com.windfall.domain.user.repository.UserRepository;
import com.windfall.global.exception.ErrorCode;
import com.windfall.global.exception.ErrorException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private AuctionRepository auctionRepository;
  @Mock private TradeRepository tradeRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private UserRepository userRepository;
  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private AuctionStateService auctionStateService;
  @Mock private PaymentPostProcessService paymentPostProcessService;
  @Mock private WebClient webClient;

  @InjectMocks private PaymentService paymentService;

  // -----------------------------
  // 1. 정상 결제 처리 테스트
  // -----------------------------
  @Test
  void confirmPayment_should_completeTradeAndSavePayment_whenTossResponseSuccess() {


    Auction auction = mock(Auction.class);
    User seller = auction.getSeller();
    if(seller == null) throw new ErrorException(ErrorCode.NOT_FOUND_USER);
    // UserRepository 모킹 추가
    when(userRepository.findById(20L)).thenReturn(Optional.of(seller));

    Trade trade = mock(Trade.class);

    PaymentConfirmRequest request = new PaymentConfirmRequest(
        "payKey1", "order1", 1000L, 1L
    );

    when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));
    when(tradeRepository.findByAuction(auction)).thenReturn(Optional.empty());
    doNothing().when(paymentPostProcessService)
        .updateDatabaseAfterPayment(anyLong(), any(Trade.class), anyString(), anyLong());

    PaymentConfirmResponse response = paymentService.confirmPayment(request, 10L);

    verify(tradeRepository).save(any(Trade.class));
    verify(paymentPostProcessService)
        .updateDatabaseAfterPayment(eq(1L), any(Trade.class), eq("payKey1"), eq(1000L));
    assertEquals("order1", response.getOrderId());
  }

  // -----------------------------
  // 2. 멱등성 테스트
  // -----------------------------
  @Test
  void confirmPayment_should_returnSameResult_whenCalledMultipleTimesForSameOrder() {
    Auction auction = mock(Auction.class);
    User seller = auction.getSeller();
    if(seller == null) throw new ErrorException(ErrorCode.NOT_FOUND_USER);
    // UserRepository 모킹 추가
    when(userRepository.findById(20L)).thenReturn(Optional.of(seller));


    Trade existingTrade = mock(Trade.class);

    PaymentConfirmRequest request = new PaymentConfirmRequest(
        "payKey1", "order1", 1000L, 1L
    );

    PaymentConfirmResponse response1 = paymentService.confirmPayment(request, 10L);
    PaymentConfirmResponse response2 = paymentService.confirmPayment(request, 10L);

    // tradeRepository.save()가 추가로 호출되지 않아야 함
    verify(tradeRepository, times(0)).save(any(Trade.class));
    assertEquals(response1.getOrderId(), response2.getOrderId());
  }

  // -----------------------------
  // 3. 예외 처리: Auction 없음
  // -----------------------------
  @Test
  void confirmPayment_should_throwError_whenAuctionNotFound() {
    PaymentConfirmRequest request = new PaymentConfirmRequest(
        "payKey1", "order1", 1000L, 999L
    );

    assertThrows(ErrorException.class,
        () -> paymentService.confirmPayment(request, 10L));
  }

  // -----------------------------
  // 4. 예외 처리: Seller 없음
  // -----------------------------
  @Test
  void confirmPayment_should_throwError_whenSellerIsNull() {
    PaymentConfirmRequest request = new PaymentConfirmRequest(
        "payKey1", "order1", 1000L, 1L
    );

    // Auction 반환, 하지만 seller는 null
    Auction auction = mock(Auction.class);
    when(auction.getSeller()).thenReturn(null);
    when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));

    assertThrows(ErrorException.class,
        () -> paymentService.confirmPayment(request, 10L));
  }

  // -----------------------------
  // 5. 예외 처리: 결제 금액 불일치
  // -----------------------------
  @Test
  void confirmPayment_should_throwError_whenPaymentAmountMismatch() {
    PaymentConfirmRequest request = new PaymentConfirmRequest(
        "payKey1",
        "order1",
        2000L, // 실제 금액과 다른 값
        1L
    );

    // auction + seller 모킹
    Auction auction = mock(Auction.class);
    User seller = mock(User.class);
    when(seller.getId()).thenReturn(20L);
    when(auction.getSeller()).thenReturn(seller);
    when(auctionRepository.findById(1L)).thenReturn(Optional.of(auction));

    // 실제로 confirmPayment에서는 userRepository.findById 호출
    // lenient()는 호출 여부에 따라 선택
    when(userRepository.findById(20L)).thenReturn(Optional.of(seller));

    assertThrows(ErrorException.class,
        () -> paymentService.confirmPayment(request, 10L));
  }
}