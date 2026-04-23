package com.windfall.api.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.windfall.api.auction.dto.request.AuctionCreateRequest;
import com.windfall.domain.auction.entity.Auction;
import com.windfall.domain.auction.enums.AuctionCategory;
import com.windfall.domain.auction.repository.AuctionRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.repository.TradeRepository;
import com.windfall.domain.user.entity.User;
import com.windfall.domain.user.enums.ProviderType;
import com.windfall.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslTestConfig.class)
class PaymentConfirmRepositoryTest {

  @Autowired
  private TradeRepository tradeRepository;

  @Autowired
  private AuctionRepository auctionRepository;

  @Autowired
  private UserRepository userRepository;

  @Test
  @DisplayName("Auction으로 Trade 조회가 정상 동작한다")
  void givenAuction_whenFindByAuction_thenReturnTrade() {
    // given
    User seller = userRepository.save(
        new User(ProviderType.GOOGLE, "user_id_1",
            "user_id_1@gmail.com", "nickname_user_id_1",
            "profile_image_url_user_1")
    );

    AuctionCreateRequest request = new AuctionCreateRequest(
        "test auction",
        "test description",
        AuctionCategory.CLOTHING,
        List.of(),              // tags (optional or empty)
        List.of(1L, 2L),        // imageIds (필수)
        1000L,                  // startPrice
        500L,                   // stopLoss
        100L,                   // dropAmount
        LocalDateTime.now().plusHours(1) // startAt (미래 시간 권장)
    );
    Auction auction = Auction.create(request, seller);
    auctionRepository.save(auction);

    Trade trade = tradeRepository.save(
        Trade.builder()
            .auction(auction)
            .sellerId(seller.getId())
            .buyerId(999L)
            .finalPrice(1000L)
            .build()
    );

    // when
    Optional<Trade> result = tradeRepository.findByAuction(auction);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(trade.getId());
    assertThat(result.get().getAuction().getId()).isEqualTo(auction.getId());
  }

  @Test
  void givenUser_whenExistsByUserId_thenReturnTrue() {
    User user = userRepository.save(
        new User(ProviderType.GOOGLE, "user_id_1",
            "user_id_1@gmail.com", "nickname_user_id_1",
            "profile_image_url_user_1")
    );

    assertTrue(userRepository.existsById(user.getId()));
  }

  @Test
  void givenUser_whenNotExistsByUserId_thenReturnFalse() {
    User user = userRepository.save(
        new User(ProviderType.GOOGLE, "user_id_1",
            "user_id_1@gmail.com", "nickname_user_id_1",
            "profile_image_url_user_1")
    );

    assertFalse(userRepository.existsById(999L));
  }
}