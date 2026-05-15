import { useMemo } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from 'recharts';
import type { Laporan } from '../types/laporan';

interface TrendChartProps {
  data: Laporan[];
  isLoading?: boolean;
}

function aggregateByDate(reports: Laporan[]) {
  const map = new Map<string, { date: string; aman: number; waspada: number; bahaya: number }>();
  reports.forEach((r) => {
    const date = r.created_at.slice(0, 10);
    if (!map.has(date)) {
      map.set(date, { date, aman: 0, waspada: 0, bahaya: 0 });
    }
    const entry = map.get(date)!;
    if (r.status === 'AMAN') entry.aman++;
    else if (r.status === 'WASPADA') entry.waspada++;
    else entry.bahaya++;
  });
  return Array.from(map.values()).sort((a, b) => a.date.localeCompare(b.date));
}

const CustomTooltip = ({ active, payload, label }: Record<string, unknown>) => {
  if (!active || !payload || !Array.isArray(payload)) return null;
  return (
    <div className="rounded-lg border border-divider bg-card px-3 py-2 shadow-lg text-xs">
      <p className="font-semibold text-text-primary mb-1">{label as string}</p>
      {(payload as Array<{ color: string; name: string; value: number }>).map((p) => (
        <div key={p.name} className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full" style={{ backgroundColor: p.color }} />
          <span className="text-text-secondary capitalize">{p.name}:</span>
          <span className="font-medium text-text-primary tabular-nums">{p.value}</span>
        </div>
      ))}
    </div>
  );
};

export function TrendChart({ data, isLoading }: TrendChartProps) {
  const chartData = useMemo(() => aggregateByDate(data), [data]);

  if (isLoading) {
    return (
      <div className="rounded-2xl bg-card border border-divider p-5 sm:p-6 animate-pulse">
        <div className="h-4 w-32 bg-divider/50 rounded mb-4" />
        <div className="h-48 sm:h-64 bg-divider/20 rounded" />
      </div>
    );
  }

  if (chartData.length < 2) {
    return (
      <div className="rounded-2xl bg-card border border-divider p-5 sm:p-6">
        <h3 className="text-sm font-semibold text-text-primary mb-1">Tren Harian</h3>
        <p className="text-xs text-text-secondary">Data belum cukup untuk grafik tren.</p>
      </div>
    );
  }

  return (
    <div className="rounded-2xl bg-card border border-divider p-5 sm:p-6">
      <h3 className="text-sm font-semibold text-text-primary mb-4">Tren Harian</h3>
      <div className="h-[220px] sm:h-[280px]">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartData} margin={{ top: 5, right: 10, left: -5, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-divider)" strokeOpacity={0.5} />
            <XAxis dataKey="date" tick={{ fontSize: 11, fill: 'var(--color-text-secondary)' }} tickLine={false} />
            <YAxis tick={{ fontSize: 11, fill: 'var(--color-text-secondary)' }} tickLine={false} allowDecimals={false} />
            <Tooltip content={<CustomTooltip />} />
            <Legend
              wrapperStyle={{ fontSize: 12, paddingTop: 8 }}
              formatter={(v: string) => <span className="text-text-secondary capitalize">{v}</span>}
            />
            <Line type="monotone" dataKey="aman" name="aman" stroke="#388E3C" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
            <Line type="monotone" dataKey="waspada" name="waspada" stroke="#F57C00" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
            <Line type="monotone" dataKey="bahaya" name="bahaya" stroke="#D32F2F" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
