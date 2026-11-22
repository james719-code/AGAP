import { cn } from '@/lib/utils';

export interface StatStripItem {
  key: string;
  label: string;
  value: number | string | null | undefined;
  tone?: 'critical' | 'moderate' | 'info' | 'resolved' | 'neutral';
  note?: string;
}

/**
 * A connected row of counters. When `onSelect` is given, each cell doubles as a
 * filter toggle, so the summary and the table filter are the same control.
 */
export function StatStrip({
  items,
  label,
  activeKey,
  onSelect,
}: {
  items: StatStripItem[];
  label: string;
  activeKey?: string;
  onSelect?: (key: string) => void;
}) {
  return (
    <section aria-label={label} className="stat-strip surface-panel" style={{ ['--stat-cols' as string]: items.length }}>
      {items.map(({ key, label: itemLabel, value, tone = 'neutral', note }) => {
        const content = (
          <>
            <span className="flex items-center gap-2 text-[0.75rem] font-semibold text-muted-foreground">
              <span className="kpi-swatch" aria-hidden="true" />
              {itemLabel}
            </span>
            <span className="metric-figure mt-2 block text-[1.75rem]">{value ?? '—'}</span>
            {note && <span className="mt-1 block text-[0.6875rem] text-muted-foreground">{note}</span>}
          </>
        );
        return onSelect ? (
          <button
            key={key}
            type="button"
            data-tone={tone}
            aria-pressed={activeKey === key}
            onClick={() => onSelect(key)}
            className={cn('kpi-cell stat-cell text-left', activeKey === key && 'stat-cell-active')}
          >
            {content}
          </button>
        ) : (
          <div key={key} data-tone={tone} className="kpi-cell stat-cell">{content}</div>
        );
      })}
    </section>
  );
}
