package com.arielsoto.spendtracker.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptMetadataRepository extends JpaRepository<ReceiptMetadata, UUID> {
    Optional<ReceiptMetadata> findBySpendId(UUID spendId);
    void deleteBySpendId(UUID spendId);
}
