import type { ReactNode } from 'react';

/**
 * Task 44 variance analysis: comparison, drill, annotation, and follow-up views with
 * certification labels and entitlement preservation. Zero-baseline percentages render
 * as undefined; unavailable results are labelled, never fabricated.
 */
export interface VarianceRow {
  readonly kpiId: string;
  readonly dimensionKey: string;
  readonly basis: 'BUDGET' | 'FORECAST' | 'PRIOR_PERIOD';
  readonly actual: string;
  readonly plan: string;
  readonly amountVariance: string;
  readonly percentage: string;
  readonly percentageState: 'COMPUTED' | 'UNDEFINED_ZERO_BASELINE' | 'UNAVAILABLE';
  readonly certified: boolean;
  readonly entitled: boolean;
}

export const VARIANCE_ROWS: readonly VarianceRow[] = [
  {
    kpiId: 'kpi-cash',
    dimensionKey: 'ENT-101|TOTAL',
    basis: 'BUDGET',
    actual: '1100.00',
    plan: '1000.00',
    amountVariance: '+100.00',
    percentage: '+10.000000%',
    percentageState: 'COMPUTED',
    certified: true,
    entitled: true,
  },
  {
    kpiId: 'kpi-cash',
    dimensionKey: 'ENT-102|TOTAL',
    basis: 'BUDGET',
    actual: '0.00',
    plan: '0.00',
    amountVariance: '0.00',
    percentage: 'Undefined (zero baseline)',
    percentageState: 'UNDEFINED_ZERO_BASELINE',
    certified: true,
    entitled: false,
  },
];

export function VarianceAnalysisPanel({
  rows,
}: {
  rows: readonly VarianceRow[];
}): ReactNode {
  return (
    <section aria-labelledby="variance-heading">
      <h3 id="variance-heading">Variance analysis</h3>
      <p>
        Comparisons run on the certified catalog&apos;s shared formula version.
        Zero-baseline percentages are undefined, never infinite; uncertified scope and
        null values render as unavailable — never as zero.
      </p>
      <table className="pack-table">
        <caption>Actual versus plan with certification and entitlement labels</caption>
        <thead>
          <tr>
            <th scope="col">KPI</th>
            <th scope="col">Dimension</th>
            <th scope="col">Basis</th>
            <th scope="col">Actual</th>
            <th scope="col">Plan</th>
            <th scope="col">Amount</th>
            <th scope="col">Percentage</th>
            <th scope="col">Status</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={`${row.kpiId}-${row.dimensionKey}-${row.basis}`}
              data-testid={`variance-${row.dimensionKey}`}
            >
              <th scope="row">{row.kpiId}</th>
              <td>{row.dimensionKey}</td>
              <td>{row.basis}</td>
              <td>
                {row.actual}{' '}
                <span className={row.certified ? 'badge badge-ok' : 'badge badge-blocked'}>
                  {row.certified ? 'Certified' : 'Uncertified — excluded'}
                </span>
              </td>
              <td>{row.plan}</td>
              <td>{row.amountVariance}</td>
              <td>{row.percentage}</td>
              <td>
                {row.entitled ? (
                  row.percentageState === 'COMPUTED' ? (
                    'Computed'
                  ) : (
                    'Governed unavailable'
                  )
                ) : (
                  'Not entitled'
                )}
              </td>
              <td>
                <button type="button">Drill</button> <button type="button">Comment</button>{' '}
                <button type="button">Follow up</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
