import { Wallet, BarChart3, BadgeCheck } from 'lucide-react';
import type { StockPosition, SummaryMetric, TimeFrame } from '../types';

export const PORTFOLIO_DATA: StockPosition[] = [
  { ticker: 'AAPL', name: 'Apple Inc.', avgCost: 150.20, price: 189.30, dailyChange: 1.2, totalGain: 4500 },
  { ticker: 'TSLA', name: 'Tesla, Inc.', avgCost: 210.50, price: 238.15, dailyChange: -0.5, totalGain: 2800 },
  { ticker: 'NVDA', name: 'NVIDIA Corp', avgCost: 420.00, price: 485.20, dailyChange: 3.4, totalGain: 12000 },
  { ticker: 'MSFT', name: 'Microsoft', avgCost: 310.00, price: 335.40, dailyChange: 0.8, totalGain: 5100 },
];

export const SUMMARY_CARDS: SummaryMetric[] = [
  { title: 'Cash Available', value: '$45,200.00', icon: Wallet, colorClass: 'text-sky-300', bgClass: 'bg-blue-400/5' },
  { title: 'Est. Dividends', value: '$1,840.12', icon: BarChart3, colorClass: 'text-purple-300', bgClass: 'bg-purple-400/5' },
  { title: 'Portfolio Yield', value: '3.24%', icon: BadgeCheck, colorClass: 'text-orange-300', bgClass: 'bg-orange-400/5' },
];

export const TIME_FRAMES: TimeFrame[] = ['D', 'W', 'M', 'Y', '5Y', 'YTD', 'ALL'];