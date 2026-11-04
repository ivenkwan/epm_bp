import type { ReactNode } from 'react';

/**
 * Task 55 advanced administration: governed promotions of rules, processes, prompts,
 * and models with isolated simulation, fresh impact, independent approval, full
 * provenance, and rollback references. Nothing deploys itself.
 */
export interface PromotionRow {
  readonly change: string;
  readonly kind: 'DRIVER' | 'ALLOCATION' | 'ELIMINATION' | 'PROCESS' | 'PROMPT' | 'MODEL';
  readonly state: 'SIMULATED' | 'PROMOTED' | 'BLOCKED' | 'QUARANTINED';
  readonly note: string;
}

export const PROMOTIONS: readonly PromotionRow[] = [
  {
    change: 'Driver formula v2',
    kind: 'DRIVER',
    state: 'PROMOTED',
    note: 'rollback-to:v1 preserved',
  },
  {
    change: 'Prompt with embedded key',
    kind: 'PROMPT',
    state: 'QUARANTINED',
    note: 'suspected secret — human remediation',
  },
];

export function AdministrationPanel({ rows }: { rows: readonly PromotionRow[] }): ReactNode {
  return (
    <section aria-labelledby="admin-heading">
      <h3 id="admin-heading">Rule &amp; process administration</h3>
      <p>
        Promotions require a passing isolated simulation, fresh AGE impact, an
        independent approver, and the full provenance chain (ticket, tests, SBOM,
        runbook, doc delta). Suspected secrets and undocumented changes quarantine;
        rejected promotions leave active versions untouched. Java is the only
        authoritative commit path — nothing deploys itself.
      </p>
      <table className="pack-table">
        <caption>Governed promotions with provenance state</caption>
        <thead>
          <tr>
            <th scope="col">Change</th>
            <th scope="col">Kind</th>
            <th scope="col">State</th>
            <th scope="col">Note</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={`${row.change}-${row.note}`}>
              <th scope="row">{row.change}</th>
              <td>{row.kind}</td>
              <td>
                <span
                  className={row.state === 'PROMOTED' ? 'badge badge-ok' : 'badge badge-blocked'}
                >
                  {row.state}
                </span>
              </td>
              <td>{row.note}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
