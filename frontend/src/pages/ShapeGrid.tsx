import { useEffect, useRef, forwardRef, useImperativeHandle } from 'react';

type ShapeKind = 'square' | 'hexagon' | 'circle' | 'triangle';
type Direction = 'up' | 'down' | 'left' | 'right' | 'diagonal';

interface ShapeGridProps {
  /** Drift speed of the grid pattern, in pixels/frame-ish units. */
  speed?: number;
  /** Size of each cell/shape in pixels. */
  size?: number;
  /** Alias for `size`, kept for compatibility. */
  squareSize?: number;
  /** Direction the grid quietly drifts in. */
  direction?: Direction;
  /** Shape drawn at each grid cell. */
  shape?: ShapeKind;
  /** Hairline color for the undrawn grid / shape stroke. */
  borderColor?: string;
  /** Fill color applied to a cell under/near the cursor (use rgba(...) so alpha can be tweened). */
  hoverColor?: string;
  /** Alias for `hoverColor`, kept for compatibility. */
  hoverFillColor?: string;
  /** How many trailing cells stay lit behind the cursor (0 = none). */
  hoverTrailAmount?: number;
  /** Stroke width for the grid lines / shape outlines. */
  lineWidth?: number;
  className?: string;
}

/** Imperative handle so a parent covering a larger hit area (e.g. the whole
 *  hero section, including content sitting on top of this canvas) can drive
 *  the hover trail even though the canvas itself never receives the
 *  mousemove event. */
export interface ShapeGridHandle {
  /** Coordinates are relative to this component's own container. Pass null on mouse leave. */
  setMouse: (point: { x: number; y: number } | null) => void;
}

interface TrailPoint {
  col: number;
  row: number;
  life: number; // 1 -> 0
}

/**
 * A quiet, canvas-based grid of shapes that slowly drifts and softly
 * lights up near the cursor. Intentionally minimal — no particle system,
 * no physics — just a drawn grid re-rendered on a rAF loop.
 */
const ShapeGrid = forwardRef<ShapeGridHandle, ShapeGridProps>(({
  speed = 0.3,
  size,
  squareSize,
  direction = 'diagonal',
  shape = 'square',
  borderColor = '#C9C4B8',
  hoverColor = 'rgba(209, 80, 51, 0.18)',
  hoverFillColor,
  hoverTrailAmount = 2,
  lineWidth = 1.25,
  className = ''
}, ref) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mouseRef = useRef<{ x: number; y: number } | null>(null);
  const trailRef = useRef<TrailPoint[]>([]);
  const offsetRef = useRef({ x: 0, y: 0 });
  const rafRef = useRef<number | null>(null);

  const cell = size ?? squareSize ?? 40;
  const fillColorBase = hoverColor ?? hoverFillColor ?? 'rgba(209, 80, 51, 0.10)';

  // Let a parent element (e.g. one that wraps this grid plus foreground
  // content) drive the hover point directly, since pointer events over the
  // foreground content never reach this canvas.
  useImperativeHandle(ref, () => ({
    setMouse: (point) => {
      mouseRef.current = point;
    }
  }), []);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);

    const resize = () => {
      const { width, height } = container.getBoundingClientRect();
      canvas.width = Math.max(1, Math.floor(width * dpr));
      canvas.height = Math.max(1, Math.floor(height * dpr));
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    resize();
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(container);

    // Still listen locally too, in case ShapeGrid is used standalone
    // without a parent driving setMouse via the ref.
    const handleMouseMove = (e: MouseEvent) => {
      const rect = container.getBoundingClientRect();
      mouseRef.current = { x: e.clientX - rect.left, y: e.clientY - rect.top };
    };
    const handleMouseLeave = () => {
      mouseRef.current = null;
    };
    container.addEventListener('mousemove', handleMouseMove);
    container.addEventListener('mouseleave', handleMouseLeave);

    const dx =
      direction === 'left' ? -1 : direction === 'right' ? 1 : direction === 'diagonal' ? 1 : 0;
    const dy = direction === 'up' ? -1 : direction === 'down' ? 1 : direction === 'diagonal' ? 1 : 0;

    // Parse "rgba(r, g, b, a)" (or rgb/hex fallback) once so we can tween alpha per-frame.
    const parseColor = (input: string): [number, number, number, number] => {
      const rgbaMatch = input.match(
        /rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)/i
      );
      if (rgbaMatch) {
        return [
          Number(rgbaMatch[1]),
          Number(rgbaMatch[2]),
          Number(rgbaMatch[3]),
          rgbaMatch[4] !== undefined ? Number(rgbaMatch[4]) : 1
        ];
      }
      const hexMatch = input.match(/^#?([0-9a-f]{6})$/i);
      if (hexMatch) {
        const n = parseInt(hexMatch[1], 16);
        return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 1];
      }
      return [209, 80, 51, 0.1];
    };
    const [fr, fg, fb, fa] = parseColor(fillColorBase);

    // Outline shapes (hexagon/circle/triangle) still draw as discrete cells —
    // a "net" reading doesn't apply to them the way it does a square mesh.
    const drawOutlineShape = (x: number, y: number, s: number, fillAlpha: number | null) => {
      ctx.beginPath();
      switch (shape) {
        case 'circle': {
          ctx.arc(x + s / 2, y + s / 2, s / 2.3, 0, Math.PI * 2);
          break;
        }
        case 'hexagon': {
          const cx = x + s / 2;
          const cy = y + s / 2;
          const r = s / 2.05;
          for (let i = 0; i < 6; i++) {
            const angle = (Math.PI / 3) * i - Math.PI / 6;
            const px = cx + r * Math.cos(angle);
            const py = cy + r * Math.sin(angle);
            if (i === 0) ctx.moveTo(px, py);
            else ctx.lineTo(px, py);
          }
          ctx.closePath();
          break;
        }
        case 'triangle': {
          const cx = x + s / 2;
          const top = y + s * 0.08;
          const bottom = y + s * 0.92;
          ctx.moveTo(cx, top);
          ctx.lineTo(x + s * 0.92, bottom);
          ctx.lineTo(x + s * 0.08, bottom);
          ctx.closePath();
          break;
        }
      }
      if (fillAlpha !== null) {
        ctx.fillStyle = `rgba(${fr}, ${fg}, ${fb}, ${fillAlpha})`;
        ctx.fill();
      } else {
        ctx.strokeStyle = borderColor;
        ctx.lineWidth = lineWidth;
        ctx.stroke();
      }
    };

    // "square" mode draws a single continuous net (connected grid lines), not
    // isolated boxes — every cell shares its edges with its neighbors.
    const drawNet = (width: number, height: number, offX: number, offY: number) => {
      ctx.beginPath();
      for (let x = -cell; x <= width + cell; x += cell) {
        const px = x + offX;
        ctx.moveTo(px, -cell);
        ctx.lineTo(px, height + cell);
      }
      for (let y = -cell; y <= height + cell; y += cell) {
        const py = y + offY;
        ctx.moveTo(-cell, py);
        ctx.lineTo(width + cell, py);
      }
      ctx.strokeStyle = borderColor;
      ctx.lineWidth = lineWidth;
      ctx.stroke();
    };

    const fillCell = (x: number, y: number, s: number, alpha: number) => {
      ctx.fillStyle = `rgba(${fr}, ${fg}, ${fb}, ${alpha})`;
      ctx.fillRect(x, y, s, s);
    };

    const render = () => {
      const { width, height } = container.getBoundingClientRect();
      ctx.clearRect(0, 0, width, height);

      offsetRef.current.x = (offsetRef.current.x + dx * speed) % cell;
      offsetRef.current.y = (offsetRef.current.y + dy * speed) % cell;
      const offX = ((offsetRef.current.x % cell) + cell) % cell;
      const offY = ((offsetRef.current.y % cell) + cell) % cell;

      const cols = Math.ceil(width / cell) + 2;
      const rows = Math.ceil(height / cell) + 2;

      const mouse = mouseRef.current;
      if (mouse) {
        const hoverCol = Math.floor((mouse.x - offX) / cell);
        const hoverRow = Math.floor((mouse.y - offY) / cell);
        const existing = trailRef.current.find((t) => t.col === hoverCol && t.row === hoverRow);
        if (existing) {
          existing.life = 1;
        } else {
          trailRef.current.unshift({ col: hoverCol, row: hoverRow, life: 1 });
        }
        const maxTrail = Math.max(1, hoverTrailAmount + 1);
        trailRef.current = trailRef.current.slice(0, maxTrail);
      }

      // decay trail
      trailRef.current.forEach((t) => (t.life *= 0.92));
      trailRef.current = trailRef.current.filter((t) => t.life > 0.02);

      if (shape === 'square') {
        for (const t of trailRef.current) {
          const x = t.col * cell + offX;
          const y = t.row * cell + offY;
          fillCell(x, y, cell, fa * t.life);
        }
        drawNet(width, height, offX, offY);
      } else {
        for (let r = -1; r < rows; r++) {
          for (let c = -1; c < cols; c++) {
            const x = c * cell + offX;
            const y = r * cell + offY;
            const trailPoint = trailRef.current.find((t) => t.col === c && t.row === r);
            if (trailPoint) {
              drawOutlineShape(x, y, cell, fa * trailPoint.life);
              continue;
            }
            drawOutlineShape(x, y, cell, null);
          }
        }
      }

      rafRef.current = requestAnimationFrame(render);
    };

    rafRef.current = requestAnimationFrame(render);

    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      resizeObserver.disconnect();
      container.removeEventListener('mousemove', handleMouseMove);
      container.removeEventListener('mouseleave', handleMouseLeave);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [speed, cell, direction, shape, borderColor, fillColorBase, hoverTrailAmount]);

  return (
    <div ref={containerRef} className={`absolute inset-0 ${className}`}>
      <canvas ref={canvasRef} className="block w-full h-full" />
    </div>
  );
});

ShapeGrid.displayName = 'ShapeGrid';

export default ShapeGrid;