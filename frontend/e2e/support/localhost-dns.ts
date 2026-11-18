import dns from 'node:dns';

/**
 * Workshops live at <slug>.localhost. Browsers resolve every *.localhost name to the loopback
 * address themselves, but Node (which runs Playwright's API requests) asks the operating system,
 * and some (Windows, for one) only know plain "localhost". Map them here, for the test run only.
 */
const LOCAL = /(^|\.)localhost\.?$/i;

type LookupCallback = (
  err: NodeJS.ErrnoException | null,
  address: string | dns.LookupAddress[],
  family?: number,
) => void;

const originalLookup = dns.lookup.bind(dns) as unknown as (
  hostname: string,
  options: dns.LookupOptions,
  callback: LookupCallback,
) => void;

function patchedLookup(hostname: string, options: unknown, callback?: unknown): void {
  const cb = (typeof options === 'function' ? options : callback) as LookupCallback;
  const opts = (typeof options === 'function' || options === undefined ? {} : options) as dns.LookupOptions;
  if (LOCAL.test(hostname) && hostname.toLowerCase() !== 'localhost') {
    if (opts.all) {
      cb(null, [{ address: '127.0.0.1', family: 4 }]);
    } else {
      cb(null, '127.0.0.1', 4);
    }
    return;
  }
  originalLookup(hostname, opts, cb);
}

const originalPromiseLookup = dns.promises.lookup.bind(dns.promises);

(dns as unknown as { lookup: unknown }).lookup = patchedLookup;
(dns.promises as unknown as { lookup: unknown }).lookup = async (
  hostname: string,
  options?: dns.LookupOptions,
) => {
  if (LOCAL.test(hostname) && hostname.toLowerCase() !== 'localhost') {
    return options?.all ? [{ address: '127.0.0.1', family: 4 }] : { address: '127.0.0.1', family: 4 };
  }
  return originalPromiseLookup(hostname, options ?? {});
};
