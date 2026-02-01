import React, { useState } from 'react';
import { 
  Activity, 
  ChevronDown, 
  TrendingUp, 
  ArrowRight, 
  Wallet, 
  BarChart3, 
  BadgeCheck, 
  type LucideIcon 
} from 'lucide-react';

// --- Types & Interfaces ---

type TimeFrame = '1D' | '1W' | '1M' | '1Y';

interface StockPosition {
  ticker: string;
  name: string;
  avgCost: number;
  price: number;
  dailyChange: number;
  totalGain: number;
}

interface SummaryMetric {
  title: string;
  value: string;
  icon: LucideIcon;
  colorClass: string;
  bgClass: string;
}

// --- Mock Data ---

const PORTFOLIO_DATA: StockPosition[] = [
  { ticker: 'AAPL', name: 'Apple Inc.', avgCost: 150.20, price: 189.30, dailyChange: 1.2, totalGain: 4500 },
  { ticker: 'TSLA', name: 'Tesla, Inc.', avgCost: 210.50, price: 238.15, dailyChange: -0.5, totalGain: 2800 },
  { ticker: 'NVDA', name: 'NVIDIA Corp', avgCost: 420.00, price: 485.20, dailyChange: 3.4, totalGain: 12000 },
  { ticker: 'MSFT', name: 'Microsoft', avgCost: 310.00, price: 335.40, dailyChange: 0.8, totalGain: 5100 },
];

const SUMMARY_CARDS: SummaryMetric[] = [
  { title: 'Cash Available', value: '$45,200.00', icon: Wallet, colorClass: 'text-sky-300', bgClass: 'bg-blue-400/5' },
  { title: 'Est. Dividends', value: '$1,840.12', icon: BarChart3, colorClass: 'text-purple-300', bgClass: 'bg-purple-400/5' },
  { title: 'Portfolio Yield', value: '3.24%', icon: BadgeCheck, colorClass: 'text-orange-300', bgClass: 'bg-orange-400/5' },
];

const TIME_FRAMES: TimeFrame[] = ['1D', '1W', '1M', '1Y'];

// --- Components ---

const Header: React.FC = () => (
  <header className="flex items-center justify-between whitespace-nowrap border-b border-[var(--border-color)] bg-[var(--bg-header)] px-10 py-5 sticky top-0 z-50">
    <div className="flex items-center gap-4">
      <div className="size-9 text-[var(--pastel-lavender)] flex items-center justify-center bg-[var(--pastel-lavender)]/10 rounded-xl">
        <Activity size={20} />
      </div>
      <h1 className="text-xl font-semibold tracking-tight text-[var(--text-primary)]">Portfolio Insights</h1>
    </div>
    <div className="flex gap-4">
      <button type="button" className="flex min-w-[120px] cursor-pointer items-center justify-center rounded-lg h-10 px-5 bg-[var(--pastel-lavender)]/90 text-[#121417] text-sm font-semibold hover:bg-[var(--pastel-lavender)] transition-colors">
        Create Report
      </button>
      <div className="relative group">
        <button type="button" className="flex min-w-[150px] cursor-pointer items-center justify-between rounded-lg h-10 px-4 bg-[var(--bg-card)] text-[var(--text-primary)] text-sm font-medium hover:bg-[var(--border-color)] transition-colors border border-[var(--border-color)]">
          <span>All Portfolios</span>
          <ChevronDown size={16} className="opacity-70" />
        </button>
      </div>
    </div>
  </header>
);

const ChartSVG: React.FC = () => (
  <svg className="chart-subtle-glow w-full h-full" fill="none" preserveAspectRatio="none" viewBox="0 0 478 150" xmlns="http://www.w3.org/2000/svg">
    <path d="M0 109C18.1538 109 18.1538 21 36.3077 21C54.4615 21 54.4615 41 72.6154 41C90.7692 41 90.7692 93 108.923 93C127.077 93 127.077 33 145.231 33C163.385 33 163.385 101 181.538 101C199.692 101 199.692 61 217.846 61C236 61 236 45 254.154 45C272.308 45 272.308 121 290.462 121C308.615 121 308.615 149 326.769 149C344.923 149 344.923 1 363.077 1C381.231 1 381.231 81 399.385 81C417.538 81 417.538 129 435.692 129C453.846 129 453.846 25 472 25V150H0V109Z" fill="url(#paint0_linear_portfolio)" />
    <path d="M0 109C18.1538 109 18.1538 21 36.3077 21C54.4615 21 54.4615 41 72.6154 41C90.7692 41 90.7692 93 108.923 93C127.077 93 127.077 33 145.231 33C163.385 33 163.385 101 181.538 101C199.692 101 199.692 61 217.846 61C236 61 236 45 254.154 45C272.308 45 272.308 121 290.462 121C308.615 121 308.615 149 326.769 149C344.923 149 344.923 1 363.077 1C381.231 1 381.231 81 399.385 81C417.538 81 417.538 129 435.692 129C453.846 129 453.846 25 472 25" stroke="#e9d5ff" strokeLinecap="round" strokeWidth="2.5" />
    <defs>
      <linearGradient id="paint0_linear_portfolio" x1="236" x2="236" y1="1" y2="150" gradientUnits="userSpaceOnUse">
        <stop stopColor="#e9d5ff" stopOpacity="0.12" />
        <stop offset="1" stopColor="#e9d5ff" stopOpacity="0" />
      </linearGradient>
    </defs>
  </svg>
);

interface AssetRowProps {
  stock: StockPosition;
}

const AssetRow: React.FC<AssetRowProps> = ({ stock }) => {
  const isPositive = stock.dailyChange >= 0;
  
  // Formatters
  const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
  const percent = new Intl.NumberFormat('en-US', { style: 'percent', minimumFractionDigits: 1, signDisplay: 'always' });

  return (
    <tr className="table-row-alternate hover:bg-white/5 transition-colors group border-b border-[var(--border-color)]/50 last:border-0">
      <td className="px-6 py-5 font-semibold text-[var(--pastel-lavender)]">{stock.ticker}</td>
      <td className="px-6 py-5 text-[var(--text-primary)] text-sm">{stock.name}</td>
      <td className="px-6 py-5 text-right text-sm text-[var(--text-secondary)]">{currency.format(stock.avgCost)}</td>
      <td className="px-6 py-5 text-right text-sm font-medium text-[var(--text-primary)]">{currency.format(stock.price)}</td>
      <td className="px-6 py-5">
        <div className="flex justify-center">
          <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold border ${
            isPositive 
              ? 'bg-[var(--pastel-mint)]/5 text-[var(--pastel-mint)] border-[var(--pastel-mint)]/20' 
              : 'bg-[var(--pastel-rose)]/5 text-[var(--pastel-rose)] border-[var(--pastel-rose)]/20'
          }`}>
            {percent.format(stock.dailyChange / 100)}
          </span>
        </div>
      </td>
      <td className="px-6 py-5 text-right text-sm text-[var(--pastel-mint)] font-medium">
        +{currency.format(stock.totalGain)}
      </td>
    </tr>
  );
};

interface MetricCardProps {
  data: SummaryMetric;
}

const MetricCard: React.FC<MetricCardProps> = ({ data }) => (
  <div className="bg-[var(--bg-card)] p-6 rounded-xl border border-[var(--border-color)] flex items-center gap-5 hover:border-[var(--pastel-lavender)]/30 transition-colors cursor-default">
    <div className={`p-3 rounded-xl ${data.bgClass} ${data.colorClass}`}>
      <data.icon size={28} />
    </div>
    <div>
      <p className="text-[9px] text-[var(--text-secondary)] font-bold uppercase tracking-widest mb-1">{data.title}</p>
      <p className="text-xl font-semibold text-[var(--text-primary)]">{data.value}</p>
    </div>
  </div>
);

// --- Main Dashboard Component ---

export default function PortfolioDashboard() {
  const [activeTimeframe, setActiveTimeframe] = useState<TimeFrame>('1M');

  return (
    <div 
      className="flex flex-col min-h-screen font-sans"
      style={{
        '--bg-main': '#121417',
        '--bg-card': '#1c1f26',
        '--bg-header': '#181b21',
        '--pastel-mint': '#bbf7d0',
        '--pastel-lavender': '#e9d5ff',
        '--pastel-rose': '#fecaca',
        '--text-primary': '#e2e8f0',
        '--text-secondary': '#94a3b8',
        '--border-color': '#2d333d',
        '--pastel-blue': '#bae6fd',
        '--pastel-orange': '#fed7aa',
        backgroundColor: 'var(--bg-main)',
        color: 'var(--text-primary)'
      } as React.CSSProperties}
    >
      <style>{`
        .chart-subtle-glow { filter: drop-shadow(0 0 4px rgba(233, 213, 255, 0.2)); }
        .table-row-alternate:nth-child(even) { background-color: rgba(45, 51, 61, 0.2); }
      `}</style>

      <Header />

      <main className="flex flex-1 justify-center py-12">
        <div className="flex flex-col w-full max-w-[1100px] px-6 gap-10">
          
          {/* Main Chart Card */}
          <div className="bg-[var(--bg-card)] rounded-xl p-8 border border-[var(--border-color)]">
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-10 gap-6">
              <div className="flex flex-col gap-2">
                <p className="text-[var(--text-secondary)] text-[10px] font-bold uppercase tracking-[0.2em]">Net Asset Value</p>
                <div className="flex items-baseline gap-4">
                  <h2 className="text-[var(--text-primary)] text-4xl font-semibold tracking-tight">$1,424,500.00</h2>
                  <span className="text-[var(--pastel-mint)] text-sm font-semibold flex items-center bg-[var(--pastel-mint)]/10 px-2.5 py-1 rounded-md">
                    <TrendingUp size={16} className="mr-1" />
                    +12.5%
                  </span>
                </div>
              </div>
              
              {/* Timeframe Selector */}
              <div className="flex w-full md:w-auto h-10 items-center justify-center rounded-lg bg-[var(--bg-main)] p-1 border border-[var(--border-color)]">
                {TIME_FRAMES.map((tf) => (
                  <button
                    key={tf}
                    type="button"
                    onClick={() => setActiveTimeframe(tf)}
                    className={`
                      flex h-full grow md:min-w-[60px] items-center justify-center rounded-md px-3 text-xs font-semibold transition-all
                      ${activeTimeframe === tf 
                        ? 'bg-[var(--border-color)] text-[var(--pastel-lavender)]' 
                        : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'}
                    `}
                  >
                    {tf}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex min-h-[300px] flex-col gap-6">
              <div className="relative w-full h-[280px]">
                <ChartSVG />
              </div>
              <div className="flex justify-between px-2 pt-4 border-t border-[var(--border-color)]">
                {['Jun 01', 'Jun 08', 'Jun 15', 'Jun 22', 'Jun 30'].map((date) => (
                  <p key={date} className="text-[var(--text-secondary)] text-[10px] font-medium tracking-widest uppercase">{date}</p>
                ))}
              </div>
            </div>
          </div>

          {/* Table Section */}
          <div className="flex flex-col gap-5">
            <div className="flex justify-between items-center px-1">
              <h2 className="text-[var(--text-primary)] text-2xl font-semibold tracking-tight">Asset Breakdown</h2>
              <button type="button" className="text-[var(--pastel-lavender)] hover:text-white transition-colors text-xs font-semibold flex items-center gap-1.5 uppercase tracking-wider">
                Full Portfolio <ArrowRight size={16} />
              </button>
            </div>
            
            <div className="overflow-hidden rounded-xl border border-[var(--border-color)] bg-[var(--bg-card)]">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="bg-[var(--bg-header)]/40 border-b border-[var(--border-color)]">
                      {['Ticker', 'Name', 'Avg Cost', 'Market Price', 'Daily G/L', 'Total Gain'].map((head, idx) => (
                        <th key={head} className={`px-6 py-4 text-[var(--text-secondary)] text-[10px] font-bold uppercase tracking-widest ${idx > 1 ? (idx === 4 ? 'text-center' : 'text-right') : ''}`}>
                          {head}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--border-color)]/50">
                    {PORTFOLIO_DATA.map((stock) => (
                      <AssetRow key={stock.ticker} stock={stock} />
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-12">
            {SUMMARY_CARDS.map((card, idx) => (
              <MetricCard key={idx} data={card} />
            ))}
          </div>

        </div>
      </main>
    </div>
  );
}