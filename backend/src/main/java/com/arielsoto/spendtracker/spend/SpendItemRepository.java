package com.arielsoto.spendtracker.spend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SpendItemRepository extends JpaRepository<SpendItem, UUID> {
    List<SpendItem> findBySpendIdOrderByPositionAsc(UUID spendId);
    void deleteBySpendId(UUID spendId);
}
