package com.porbe.app.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Número persistido autorizado para recibir informes. */
@Entity
@Table(name = "whatsapp_report_recipient")
public class WhatsAppRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "phone_number", nullable = false, unique = true, length = 15)
    private String phoneNumber;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_by", nullable = false, length = 120)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WhatsAppRecipient() {
    }

    WhatsAppRecipient(String name, String phoneNumber, boolean enabled, String updatedBy) {
        update(name, phoneNumber, enabled, updatedBy);
    }

    void update(String name, String phoneNumber, boolean enabled, String updatedBy) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
    public boolean isEnabled() { return enabled; }
    public String getUpdatedBy() { return updatedBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
