/**
 * CSV export of a report table, built in the browser from the rows on screen. Opens in Excel,
 * Numbers and Google Sheets (UTF-8 with a byte-order mark, CRLF line ends).
 */
export type CsvValue = string | number | boolean | null | undefined;

export interface CsvColumn<T> {
  header: string;
  value: (row: T) => CsvValue;
}

/**
 * One field: quoted when needed. Text that a spreadsheet would run as a formula (=, +, -, @ at
 * the start) gets a leading apostrophe, so a customer name can't become a formula.
 */
export function csvField(value: CsvValue): string {
  if (value === null || value === undefined) {
    return '';
  }
  let text = String(value);
  if (typeof value === 'string' && /^[=+\-@\t\r]/.test(text)) {
    text = `'${text}`;
  }
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

export function toCsv<T>(columns: CsvColumn<T>[], rows: T[]): string {
  const lines = [columns.map((c) => csvField(c.header)).join(',')];
  for (const row of rows) {
    lines.push(columns.map((c) => csvField(c.value(row))).join(','));
  }
  return lines.join('\r\n') + '\r\n';
}

/** Saves `csv` as a file in the browser. */
export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** "sales-2026-11-01-to-2026-11-21.csv" */
export function csvFilename(report: string, firstDay: string, lastDay: string): string {
  return firstDay === lastDay ? `${report}-${firstDay}.csv` : `${report}-${firstDay}-to-${lastDay}.csv`;
}
