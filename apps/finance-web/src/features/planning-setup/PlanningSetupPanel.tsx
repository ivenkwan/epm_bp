import type { ReactNode } from 'react';

/**
 * Task 40 planning setup: shared dimension and FX-rate selection with visible
 * governance labels — currency, FX version, scale, rounding, and missing-rate
 * quality status. Translation happens server-side; the browser never owns rates.
 */
export interface TranslatedRow {
  readonly line: string;
  readonly currency: string;
  readonly fxVersion: string;
  readonly scale: number;
  readonly rounding: 'HALF_UP' | 'HALF_EVEN' | 'DOWN';
  readonly quality: 'TRANSLATED' | 'MISSING_RATE' | 'UNAPPROVED_RATE' | 'NULL_INPUT';
  readonly groupAmount: string | null;
}

export const TRANSLATION_ROWS: readonly TranslatedRow[] = [
  {
    line: 'Revenue — EU',
    currency: 'EUR',
    fxVersion: 'fx-2026-01',
    scale: 2,
    rounding: 'HALF_UP',
    quality: 'TRANSLATED',
    groupAmount: '1085.00',
  },
  {
    line: 'Revenue — JP',
    currency: 'JPY',
    fxVersion: 'fx-2026-01',
    scale: 2,
    rounding: 'HALF_UP',
    quality: 'MISSING_RATE',
    groupAmount: null,
  },
];

export function PlanningSetupPanel({
  rows,
}: {
  rows: readonly TranslatedRow[];
}): ReactNode {
  return (
    <section aria-labelledby="planning-setup-heading">
      <h3 id="planning-setup-heading">Planning setup — dimensions &amp; FX basis</h3>
      <p>
        Planning reuses governed dimensions; translation applies exactly one approved
        rate per currency, date, and rate type. Missing rates block approval — they are
        never inferred or substituted.
      </p>
      <table className="pack-table">
        <caption>Translated analysis view with governance labels</caption>
        <thead>
          <tr>
            <th scope="col">Line</th>
            <th scope="col">Currency</th>
            <th scope="col">FX version</th>
            <th scope="col">Scale</th>
            <th scope="col">Rounding</th>
            <th scope="col">Quality</th>
            <th scope="col">Group amount</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.line} data-testid={`fx-row-${row.line}`}>
              <th scope="row">{row.line}</th>
              <td>{row.currency}</td>
              <td>{row.fxVersion}</td>
              <td>{row.scale}</td>
              <td>{row.rounding}</td>
              <td>
                <span
                  className={
                    row.quality === 'TRANSLATED' ? 'badge badge-ok' : 'badge badge-blocked'
                  }
                >
                  {row.quality === 'TRANSLATED'
                    ? 'Translated'
                    : `Blocked: ${row.quality.toLowerCase().replaceAll('_', ' ')}`}
                </span>
              </td>
              <td>{row.groupAmount ?? 'Unavailable'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
