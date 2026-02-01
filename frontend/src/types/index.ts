import type { LucideIcon } from "lucide-react";

export type TimeFrame = 'D' | 'W' | 'M' | 'YTD' | 'Y' | '5Y' | 'ALL';

export interface StockPosition {
    ticker: string;
    price: number;
    name: string;
    avgCost: number;
    dailyChange: number;
    totalGain: number;
}

export interface SummaryMetric {
    title: string;
    value: string;
    icon: LucideIcon;
    colorClass: string;
    bgClass: string;
}