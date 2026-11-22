import { useEffect, useRef, useState, type ReactNode } from 'react';
import { ArrowLeftRight } from 'lucide-react';

export function DataTableViewport({ label, children }: { label: string; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [overflowing, setOverflowing] = useState(false);

  useEffect(() => {
    const viewport = ref.current;
    if (!viewport) return;
    const measure = () => setOverflowing(viewport.scrollWidth > viewport.clientWidth + 1);
    const observer = new ResizeObserver(measure);
    observer.observe(viewport);
    if (viewport.firstElementChild) observer.observe(viewport.firstElementChild);
    const frame = requestAnimationFrame(measure);
    return () => { observer.disconnect(); cancelAnimationFrame(frame); };
  }, [children]);

  return (
    <>
      {overflowing && (
        <div className="flex items-center gap-1.5 border-b border-border bg-muted/40 px-4 py-2 text-xs text-muted-foreground">
          <ArrowLeftRight className="size-3.5" aria-hidden="true" /> Scroll horizontally to see all columns
        </div>
      )}
      <div ref={ref} className="table-scroll" role="region" aria-label={label} tabIndex={overflowing ? 0 : undefined}>
        {children}
      </div>
    </>
  );
}
