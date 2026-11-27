import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Dialog, DialogClose, DialogContent } from '@/components/ui/dialog';

/** The browser's own barcode reader (Chrome and Edge on Android; not Safari), typed here. */
interface DetectedBarcode {
  rawValue: string;
}
interface BarcodeDetectorLike {
  detect(source: HTMLVideoElement): Promise<DetectedBarcode[]>;
}
interface BarcodeDetectorClass {
  new (options: { formats: string[] }): BarcodeDetectorLike;
  getSupportedFormats(): Promise<string[]>;
}

/** Door-frame and windscreen VIN labels: Code 39 on older cars, Data Matrix or QR on newer ones. */
const VIN_FORMATS = ['code_39', 'data_matrix', 'qr_code'];
const VIN = /^[A-HJ-NPR-Z0-9]{17}$/;

function detectorClass(): BarcodeDetectorClass | undefined {
  return (globalThis as { BarcodeDetector?: BarcodeDetectorClass }).BarcodeDetector;
}

/** A 17-character VIN, without the extra leading "I" (import marker) some Code 39 labels carry. */
function asVin(text: string): string | null {
  const candidate = text.length === 18 && text.startsWith('I') ? text.slice(1) : text;
  return VIN.test(candidate) ? candidate : null;
}

/**
 * The VIN in what a barcode says: the code itself, or one word of it when the label carries
 * other text (e.g. "VIN: …"). Null when there is no VIN in it.
 */
export function vinFromBarcode(raw: string): string | null {
  const text = raw.toUpperCase();
  for (const word of text.split(/[^A-Z0-9]+/)) {
    const vin = asVin(word);
    if (vin) {
      return vin;
    }
  }
  return asVin(text.replace(/[^A-Z0-9]/g, ''));
}

/** The formats this browser can read among VIN_FORMATS (empty: no scanning here). */
async function supportedVinFormats(): Promise<string[]> {
  const Detector = detectorClass();
  if (!Detector || !navigator.mediaDevices?.getUserMedia) {
    return [];
  }
  try {
    const formats = await Detector.getSupportedFormats();
    return VIN_FORMATS.filter((f) => formats.includes(f));
  } catch {
    return [];
  }
}

/**
 * "Scan" beside a VIN field, only where the browser can read barcodes with the camera (Chrome and
 * Edge on Android). Elsewhere it renders nothing and the VIN is typed.
 */
export function VinScanButton({ onScan }: { onScan: (vin: string) => void }) {
  const [formats, setFormats] = useState<string[]>([]);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    let active = true;
    void supportedVinFormats().then((found) => active && setFormats(found));
    return () => {
      active = false;
    };
  }, []);

  if (formats.length === 0) {
    return null;
  }
  return (
    <>
      <Button type="button" size="sm" variant="outline" onClick={() => setOpen(true)}>
        Scan VIN
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          title="Scan the VIN barcode"
          description="Point the camera at the barcode on the driver's door frame or under the windscreen."
        >
          {open && (
            <ScannerView
              formats={formats}
              onScan={(vin) => {
                setOpen(false);
                onScan(vin);
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}

function ScannerView({ formats, onScan }: { formats: string[]; onScan: (vin: string) => void }) {
  const video = useRef<HTMLVideoElement>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [seen, setSeen] = useState<string | null>(null);
  const done = useRef(onScan);
  useEffect(() => {
    done.current = onScan;
  });

  useEffect(() => {
    const Detector = detectorClass();
    if (!Detector) {
      return;
    }
    const detector = new Detector({ formats });
    let stream: MediaStream | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

    const look = async () => {
      if (stopped || !video.current) {
        return;
      }
      try {
        if (video.current.readyState >= 2) {
          for (const code of await detector.detect(video.current)) {
            const vin = vinFromBarcode(code.rawValue);
            if (vin) {
              stopped = true;
              done.current(vin);
              return;
            }
            setSeen(code.rawValue);
          }
        }
      } catch {
        // A frame that can't be read: try the next one.
      }
      timer = setTimeout(() => void look(), 250);
    };

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
      .then(async (s) => {
        stream = s;
        if (stopped || !video.current) {
          s.getTracks().forEach((t) => t.stop());
          return;
        }
        video.current.srcObject = s;
        await video.current.play().catch(() => undefined);
        void look();
      })
      .catch((e: unknown) => {
        setProblem(
          e instanceof DOMException && e.name === 'NotAllowedError'
            ? 'Camera access was refused. Allow the camera for this site, or type the VIN.'
            : 'The camera could not be started. Type the VIN instead.',
        );
      });

    return () => {
      stopped = true;
      clearTimeout(timer);
      stream?.getTracks().forEach((t) => t.stop());
    };
  }, [formats]);

  return (
    <div className="grid gap-3">
      {problem ? (
        <p className="text-sm text-destructive" role="alert">
          {problem}
        </p>
      ) : (
        <>
          <video
            ref={video}
            className="aspect-video w-full rounded-md bg-black object-cover"
            muted
            playsInline
            aria-label="Camera view"
          />
          <p className="text-sm text-muted-foreground" role="status">
            {seen ? `Read "${seen}", which isn't a VIN. Try the other barcode.` : 'Looking for a barcode…'}
          </p>
        </>
      )}
      <div className="flex justify-end">
        <DialogClose asChild>
          <Button type="button" variant="ghost">
            Type it instead
          </Button>
        </DialogClose>
      </div>
    </div>
  );
}
