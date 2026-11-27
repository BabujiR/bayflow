import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';

const HEIGHT = 160;
const INK = '#0f172a';

/**
 * A box to sign in with a finger, pen or mouse. Reports the signature as a PNG data URL (null
 * while empty). The pad is white in both themes, so the ink always shows.
 */
export function SignaturePad({
  onChange,
  label = 'Sign here',
}: {
  onChange: (dataUrl: string | null) => void;
  label?: string;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const last = useRef<{ x: number; y: number } | null>(null);
  const [empty, setEmpty] = useState(true);

  // Sharp lines on high-density screens: the canvas has the device's pixels, drawn in CSS pixels.
  useEffect(() => {
    const element = canvas.current;
    if (!element) {
      return;
    }
    const ratio = Math.min(window.devicePixelRatio || 1, 3);
    const width = element.clientWidth || 480;
    element.width = Math.round(width * ratio);
    element.height = Math.round(HEIGHT * ratio);
    const context = element.getContext('2d');
    if (context) {
      context.scale(ratio, ratio);
      context.lineWidth = 2.2;
      context.lineCap = 'round';
      context.lineJoin = 'round';
      context.strokeStyle = INK;
    }
  }, []);

  const point = (event: React.PointerEvent<HTMLCanvasElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    return { x: event.clientX - box.left, y: event.clientY - box.top };
  };

  const start = (event: React.PointerEvent<HTMLCanvasElement>) => {
    event.preventDefault();
    // Keeps the stroke when the finger slides past the edge (not in every browser).
    event.currentTarget.setPointerCapture?.(event.pointerId);
    drawing.current = true;
    last.current = point(event);
  };

  const move = (event: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current || !last.current) {
      return;
    }
    const context = event.currentTarget.getContext('2d');
    const next = point(event);
    if (context) {
      context.beginPath();
      context.moveTo(last.current.x, last.current.y);
      context.lineTo(next.x, next.y);
      context.stroke();
    }
    last.current = next;
    if (empty) {
      setEmpty(false);
    }
  };

  const end = () => {
    if (!drawing.current) {
      return;
    }
    drawing.current = false;
    last.current = null;
    if (!empty && canvas.current) {
      onChange(canvas.current.toDataURL('image/png'));
    }
  };

  const clear = () => {
    const element = canvas.current;
    const context = element?.getContext('2d');
    if (element && context) {
      context.save();
      context.setTransform(1, 0, 0, 1, 0, 0);
      context.clearRect(0, 0, element.width, element.height);
      context.restore();
    }
    setEmpty(true);
    onChange(null);
  };

  return (
    <div className="grid gap-2">
      <div className="relative">
        <canvas
          ref={canvas}
          role="img"
          aria-label={label}
          className="block w-full touch-none rounded-md border border-dashed border-slate-400 bg-white"
          style={{ height: HEIGHT }}
          onPointerDown={start}
          onPointerMove={move}
          onPointerUp={end}
          onPointerLeave={end}
          onPointerCancel={end}
        />
        {empty && (
          <span className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-slate-500">
            {label}
          </span>
        )}
      </div>
      <div className="flex justify-end">
        <Button type="button" variant="ghost" size="sm" onClick={clear} disabled={empty}>
          Clear
        </Button>
      </div>
    </div>
  );
}
