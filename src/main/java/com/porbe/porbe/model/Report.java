package com.porbe.porbe.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@Entity
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;

    @Column(nullable = false)
    private Long totalGainLoss;

    @Column(nullable = false)
    private String bestPerfStock;

    @Column(nullable = false)
    private String worstPerfStock;

    @Column
    private String reportImagePath;

    @Builder.Default
    @Column(nullable = false)
    private boolean isWaMessageSent = false;

    @Column(nullable = false)
    private LocalDate weekEndingDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

}
