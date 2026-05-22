package com.windfall.api.payment.integration.trade;
// 두 트랜잭션이 같은 시점에 Trade 조회 후 생성 시,
// 먼저 조회한 트랜잭션이 있다면, 아직 커밋/롤백 안했어도 다른 트랜잭션은 생성 시도 금지되어야한다.

// 상황:
//  Thread A, B가
//   1) 동시에 시작
//   2) 동일 auctionId로 createTrade 호출
//   3) 둘 다 find에서 null을 읽음
//   4) 둘 다 insert 시도

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.windfall.api.auction.dto.request.AuctionCreateRequest;
import com.windfall.api.auction.dto.request.TagInfo;
import com.windfall.api.payment.service.PaymentService;
import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.auction.enums.AuctionCategory;
import com.windfall.domain.auction.repository.AuctionRepository;
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
public class TradeCreationUniqueConstraintTest {

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

    // when
    Future<?> a = executor.submit(() ->
        paymentService.createTradeIfAbsent(auction, 1L, 1000L)
    );

    Future<?> b = executor.submit(() ->
        paymentService.createTradeIfAbsent(auction, 2L, 2000L)
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