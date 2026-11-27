import { useQuery } from '@tanstack/react-query';

import { vehiclesApi } from '@/api/endpoints';
import { ApiError, errorMessage } from '@/api/errors';
import type { DecodedVin } from '@/api/types';

const VIN = /^[A-HJ-NPR-Z0-9]{17}$/;

/** "2019 Honda Civic EX · 2.0L 4-cyl Gasoline", or what is known. */
export function describeDecoded(d: DecodedVin): string {
  const car = [d.year, d.make, d.model, d.trim].filter(Boolean).join(' ');
  return [car, d.engine].filter(Boolean).join(' · ');
}

/**
 * Decodes a complete VIN (staff only; the server asks NHTSA vPIC). Each VIN is looked up once per
 * session: a car's specs never change.
 */
export function useVinDecode(vin: string, enabled: boolean) {
  const normalized = vin.trim().toUpperCase();
  return useQuery({
    queryKey: ['vin-decode', normalized],
    queryFn: () => vehiclesApi.decode(normalized),
    enabled: enabled && VIN.test(normalized),
    staleTime: Infinity,
    retry: (count, error) => !(error instanceof ApiError && error.isClientError) && count < 1,
  });
}

/** What the VIN lookup said, under a VIN field: the car, its warnings, or why it couldn't. */
export function VinDecodeNote({ query }: { query: ReturnType<typeof useVinDecode> }) {
  if (query.fetchStatus === 'idle' && !query.data && !query.error) {
    return null;
  }
  if (query.isFetching) {
    return <p className="text-sm text-muted-foreground">Looking up the VIN…</p>;
  }
  if (query.error) {
    return (
      <p className="text-sm text-muted-foreground" role="status">
        {query.error instanceof ApiError && query.error.code === 'vin-not-decoded'
          ? "This VIN couldn't be decoded. Check it, or enter the details by hand."
          : errorMessage(query.error)}
      </p>
    );
  }
  if (!query.data) {
    return null;
  }
  return (
    <div className="grid gap-1 text-sm" role="status">
      <p>
        <span className="text-muted-foreground">VIN decoded: </span>
        <span className="font-medium">{describeDecoded(query.data)}</span>
      </p>
      {query.data.warnings.map((warning) => (
        <p key={warning} className="text-amber-700 dark:text-amber-400">
          {warning}
        </p>
      ))}
    </div>
  );
}
