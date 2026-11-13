package com.example.holdings.holding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
    boolean existsByCode(String code);
    Optional<Holding> findByCode(String code);
    List<Holding> findAllByOrderByIdAsc();

    /** 新增、刪除交易前鎖住這檔，兩個賣出同時進來時才不會一起通過「股數夠不夠」的檢查。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Holding h where h.id = :id")
    Optional<Holding> lockById(Long id);
}
