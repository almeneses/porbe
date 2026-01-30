package com.porbe.porbe.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.porbe.porbe.enums.OperationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Operation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "portfolio_id", nullable=false, foreignKey=@ForeignKey(name="fk_operation_portfolio"))
    private Portfolio portfolio;

    @ManyToOne(optional = false)
    @JoinColumn(name = "stock_id", nullable=false, foreignKey=@ForeignKey(name="fk_operation_stock"))
    private Stock stock;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OperationType type;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private double comission;

    @Column(nullable = false)
    private double total;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

}

