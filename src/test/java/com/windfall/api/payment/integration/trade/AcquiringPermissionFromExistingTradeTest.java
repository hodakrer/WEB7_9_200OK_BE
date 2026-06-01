package com.windfall.api.payment.integration.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.windfall.api.auction.dto.request.AuctionCreateRequest;
import com.windfall.api.auction.dto.request.TagInfo;
import com.windfall.api.payment.service.PaymentService;
import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.auction.enums.AuctionCategory;
import com.windfall.domain.auction.repository.AuctionRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.domain.user.entity.User;
import com.windfall.domain.user.enums.ProviderType;
import com.windfall.domain.user.repository.UserRepository;
import com.windfall.global.config.PaymentServiceTestConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(classes = PaymentServiceTestConfig.class)
public class AcquiringPermissionFromExistingTradeTest {

  @Autowired
  TradeRepository tradeRepository;
  @Autowired
  AuctionRepository auctionRepository;
  @Autowired
  UserRepository userRepository;
  @Autowired
  PaymentService paymentService;

  private ExecutorService executor = Executors.newFixedThreadPool(2);

  private User user;
  @BeforeEach
  void setUp() {
    user = userRepository.save(
        new User(ProviderType.KAKAO, "1", "email", "nickname", "imgUrl")
    );
  }

  @Test
  void unique_constraint_allows_only_one_trade_per_auction() throws Exception {

    // given
    AuctionCreateRequest request = new AuctionCreateRequest("title", "description",
        AuctionCategory.CLOTHING, new ArrayList<TagInfo>(), new ArrayList<Long>(),
        1L, 1L, 1L, LocalDateTime.now());
    userRepository.save(user);
    Auction auction = auctionRepository.save(
        Auction.create(request, user));

    Trade trade = Trade.builder()
        .auction(auction)
        .buyerId(1L)
        .sellerId(user.getId())
        .finalPrice(1000L)
        .build();

    trade.changeStatus(TradeStatus.PAYMENT_CANCELED);
    tradeRepository.saveAndFlush(trade);

    // when
    Future<?> a = executor.submit(() ->
        paymentService.acquirePaymentRequestPermission(auction, 3L, 1000L)
    );

    Future<?> b = executor.submit(() ->
        paymentService.acquirePaymentRequestPermission(auction, 2L, 2000L)
    );

    Exception exA = null;
    Exception exB = null;

    try {
      a.get();
    } catch (ExecutionException e) {
      exA = e;
    }

    try {
      b.get();
    } catch (ExecutionException e) {
      exB = e;
    }

    // then
    long count = tradeRepository.countByAuction(auction);

    assertEquals(1, count);

    // 최소 하나는 무조건 실패해야 정상
    assertTrue(exA != null || exB != null);
  }
}