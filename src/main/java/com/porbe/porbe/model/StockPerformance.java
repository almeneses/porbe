package com.porbe.porbe.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class StockPerformance {
    private double percentChange;
    private double priceChange;
    private Stock stock;
    
}
