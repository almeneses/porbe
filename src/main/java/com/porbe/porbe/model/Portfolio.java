package com.porbe.porbe.model;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Portfolio {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;

        @Column(nullable = false)
        private String owner;

        @Column
        private double total;

        @Column
        private double cash;

        @OneToMany(mappedBy = "portfolio", orphanRemoval = true)
        private Set<PortfolioStock> portfolioStocks;

        @OneToMany(mappedBy = "portfolio", orphanRemoval = true)
        private Set<Operation> portfolioOperations;

        public void addStock(Stock stock, int quantity) {
                portfolioStocks.add(PortfolioStock.builder().portfolio(this)
                                .stock(stock)
                                .quantity(quantity)
                                .build());
        }

        public void addOperation(Operation operation) {
                portfolioOperations.add(operation);
        }
}
