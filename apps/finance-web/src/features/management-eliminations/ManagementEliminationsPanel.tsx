import type { ReactNode } from 'react';

/**
 * Task 50 management-only eliminations: matched counterparty pairs, mismatch cases,
 * and the reconciled bridge — all prominently labelled MANAGEMENT ONLY, never
 * statutory consolidation.
 */
export interface EliminationRow {
  readonly key: string;
  readonly left: string;
  readonly right: string;
  readonly leftAmount: string;
  readonly rightAmount: string;
  readonly eliminated: string | null;
  readonly outcome: 'ELIMINATED' | 'MISMATCH_CASE' | 'ONE_SIDED_CASE';
}

export const ELIMINATIONS: readonly EliminationRow[] = [
  {
    key: 'IC-LOAN-1',
    left: 'ENT-101 PAY',
    right: 'ENT-102 RECEIVE',
    leftAmount: '500.00',
    rightAmount: '500.00',
    eliminated: '500.00',
    outcome: 'ELIMINATED',
  },
  {
    key: 'IC-1',
    left: 'ENT-101 PAY',
    right: 'ENT-102 RECEIVE',
    leftAmount: '500.00',
    rightAmount: '480.00',
    eliminated: null,
    outcome: 'MISMATCH_CASE',
  },
];

export function ManagementEliminationsPanel({
  rows,
}: {
  rows: readonly EliminationRow[];
}): ReactNode {
  return (
    <section aria-labelledby="eliminations-heading">
      <h3 id="eliminations-heading">Management-only eliminations</h3>
      <p>
        <strong>MANAGEMENT ONLY — not statutory consolidation.</strong> Matched pairs
        eliminate exactly once under approved runs; mismatches and one-sided lines open
        cases with entity owners — never automatic elimination; both sides and all
        differences are preserved.
      </p>
      <table className="pack-table">
        <caption>Counterparty matching and management-only elimination ledger</caption>
        <thead>
          <tr>
            <th scope="col">Key</th>
            <th scope="col">Left</th>
            <th scope="col">Right</th>
            <th scope="col">Amounts</th>
            <th scope="col">Eliminated</th>
            <th scope="col">Outcome</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.key} data-testid={`elim-${row.key}`}>
              <th scope="row">{row.key}</th>
              <td>{row.left}</td>
              <td>{row.right}</td>
              <td>
                {row.leftAmount} / {row.rightAmount}
              </td>
              <td>{row.eliminated ?? '—'}</td>
              <td>
                <span
                  className={
                    row.outcome === 'ELIMINATED' ? 'badge badge-ok' : 'badge badge-blocked'
                  }
                >
                  {row.outcome === 'ELIMINATED'
                    ? 'Eliminated (management only)'
                    : row.outcome === 'MISMATCH_CASE'
                      ? 'Mismatch case opened'
                      : 'One-sided case opened'}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
