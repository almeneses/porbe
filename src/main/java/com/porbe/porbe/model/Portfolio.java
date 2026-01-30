package com.porbe.porbe.model;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

        @Column
        private double total;

        @Column
        private double cash;

        @ManyToOne(optional = false)
        @JoinColumn(name="user_id", nullable=false, foreignKey=@ForeignKey(name="fk_porfolio_user"))
        private User user;

        @OneToMany(mappedBy = "portfolio", orphanRemoval = true)
        private Set<PortfolioStock> portfolioStocks;

        @OneToMany(mappedBy = "portfolio", orphanRemoval = true)
        private Set<Operation> portfolioOperations;

        public void addStock(Stock stock, int quantity) {
                portfolioStocks.add(PortfolioStock.builder()
                                .stock(stock)
                                .quantity(quantity)
                                .build());
        }

        public void addOperation(Operation operation) {
                portfolioOperations.add(operation);
        }
}
