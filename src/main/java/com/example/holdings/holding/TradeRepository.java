package com.example.holdings.holding;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByHoldingIdOrderByTradeDateAscIdAsc(Long holdingId);
    List<Trade> findAllByOrderByHoldingIdAscTradeDateAscIdAsc();
    long countByHoldingId(Long holdingId);
}
