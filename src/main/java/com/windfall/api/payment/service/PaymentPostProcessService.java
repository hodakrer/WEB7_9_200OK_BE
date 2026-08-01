package com.windfall.api.payment.service;

import com.windfall.api.auction.service.AuctionStateService;
import com.windfall.domain.chat.entity.ChatRoom;
import com.windfall.domain.chat.repository.ChatRoomRepository;
import com.windfall.domain.payment.entity.Payment;
import com.windfall.domain.payment.enums.PaymentStatus;
import com.windfall.domain.payment.repository.PaymentRepository;
import com.windfall.domain.trade.entity.Trade;
import com.windfall.domain.trade.enums.TradeStatus;
import com.windfall.domain.trade.repository.TradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentPostProcessService {

  private final AuctionStateService auctionStateService;
  private final PaymentRepository paymentRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final TradeRepository tradeRepository;

  public PaymentPostProcessService(
      AuctionStateService auctionStateService,
      PaymentRepository paymentRepository,
      ChatRoomRepository chatRoomRepository,
      TradeRepository tradeRepository
  ) {
    this.auctionStateService = auctionStateService;
    this.paymentRepository = paymentRepository;
    this.chatRoomRepository = chatRoomRepository;
    this.tradeRepository = tradeRepository;
  }

  @Transactional
  public void updateDatabaseAfterPayment(Long auctionId, Trade trade, String paymentKey, Long amount) {
    // 사후 처리용
    // trade 객체, payment 객체 값과 status 변경, 저장.
    // payment 객체 생성, 값 넣고 저장.
    // 채팅방 객체도 생성, 저장.

    auctionStateService.completeAuction(auctionId);

    // trade.changeStatus → 벌크 UPDATE
    tradeRepository.updateStatus(trade.getId(), TradeStatus.PAYMENT_COMPLETED);

    // PaymentPreProcessService 때 생성한 payment의 상태를 갱신
    Payment payment = paymentRepository.findByPaymentKey(paymentKey)
        .orElseThrow(() -> new IllegalStateException(
            "선점 시 생성됐어야 할 Payment 없음. paymentKey=" + paymentKey));
    // 영속 상태 → 커밋 시 자동 UPDATE
    payment.changeStatus(PaymentStatus.DONE);

    ChatRoom chatRoom = ChatRoom.generateChatRoom(trade);
    chatRoomRepository.save(chatRoom);
  }
}
