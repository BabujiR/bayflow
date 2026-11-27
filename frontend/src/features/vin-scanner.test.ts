import { describe, expect, it } from 'vitest';

import { vinFromBarcode } from '@/features/vehicles/VinScanner';

describe('reading a VIN from a barcode', () => {
  it('takes the VIN as printed', () => {
    expect(vinFromBarcode('1HGCM82633A004352')).toBe('1HGCM82633A004352');
    expect(vinFromBarcode('1hgcm82633a004352')).toBe('1HGCM82633A004352');
  });

  it('drops the import marker some Code 39 labels start with', () => {
    expect(vinFromBarcode('I1HGCM82633A004352')).toBe('1HGCM82633A004352');
  });

  it('finds the VIN inside other text', () => {
    expect(vinFromBarcode('VIN: 2T1BURHE2JC034501 / MFD 03-18')).toBe('2T1BURHE2JC034501');
  });

  it('says so when there is no VIN', () => {
    expect(vinFromBarcode('TIRE PRESSURE 35 PSI')).toBeNull();
    expect(vinFromBarcode('1HGCM8263')).toBeNull();
  });
});
