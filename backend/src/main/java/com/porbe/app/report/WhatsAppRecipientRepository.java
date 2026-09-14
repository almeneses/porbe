package com.porbe.app.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppRecipientRepository extends JpaRepository<WhatsAppRecipient, Long> {

    List<WhatsAppRecipient> findAllByOrderByNameAsc();

    List<WhatsAppRecipient> findByEnabledTrueOrderByNameAsc();

    Optional<WhatsAppRecipient> findByPhoneNumber(String phoneNumber);

    boolean existsByEnabledTrue();
}
