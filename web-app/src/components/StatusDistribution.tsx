import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';

interface StatusDistributionProps {
  aman: number;
  waspada: number;
  bahaya: number;
  isLoading?: boolean;
}

const COLOR_VARS = { aman: 'var(--color-aman)', waspada: 'var(--color-waspada)', bahaya: 'var(--color-bahaya)' };

const CustomTooltip = ({ active, payload }: Record<string, unknown>) => {
  if (!active || !payload || !Array.isArray(payload)) return null;
  const data = payload[0] as { name: string; value: number; payload: { percent: number; fill: string } } | undefined;
  if (!data) return null;
  return (
    <div className="rounded-lg border border-divider bg-card px-3 py-2 shadow-lg text-xs">
      <div className="flex items-center gap-2">
        <span className="h-2 w-2 rounded-full" style={{ backgroundColor: data.payload?.fill }} />
        <span className="text-text-secondary capitalize">{data.name}:</span>
        <span className="font-medium text-text-primary tabular-nums">{data.value}</span>
        <span className="text-text-secondary/60">({(data.payload?.percent * 100).toFixed(0)}%)</span>
      </div>
    </div>
  );
};

export function StatusDistribution({ aman, waspada, bahaya, isLoading }: StatusDistributionProps) {
  if (isLoading) {
    return (
      <div className="rounded-2xl bg-card border border-divider p-5 sm:p-6 animate-pulse">
        <div className="h-4 w-36 bg-divider/50 rounded mb-4" />
        <div className="h-44 bg-divider/20 rounded-full w-44 mx-auto" />
      </div>
    );
  }

  const total = aman + waspada + bahaya;
  if (total === 0) {
    return (
      <div className="rounded-2xl bg-card border border-divider p-5 sm:p-6">
        <h3 className="text-sm font-semibold text-text-primary mb-1">Distribusi Status</h3>
        <p className="text-xs text-text-secondary">Belum ada data laporan.</p>
      </div>
    );
  }

  const chartData = [
    { name: 'aman', value: aman, fill: COLOR_VARS.aman },
    { name: 'waspada', value: waspada, fill: COLOR_VARS.waspada },
    { name: 'bahaya', value: bahaya, fill: COLOR_VARS.bahaya },
  ];

  return (
    <div className="rounded-2xl bg-card border border-divider p-5 sm:p-6">
      <h3 className="text-sm font-semibold text-text-primary mb-2">Distribusi Status</h3>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie data={chartData} cx="50%" cy="50%" innerRadius={50} outerRadius={80} paddingAngle={3} dataKey="value">
            {chartData.map((entry) => (
              <Cell key={entry.name} fill={entry.fill} stroke="var(--color-card)" strokeWidth={2} />
            ))}
          </Pie>
          <Tooltip content={<CustomTooltip />} />
          <Legend
            wrapperStyle={{ fontSize: 12, color: 'var(--color-text-secondary)' }}
            formatter={(v: string) => <span style={{ color: 'var(--color-text-secondary)', textTransform: 'capitalize' }}>{v}</span>}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
