import type { ReactNode } from 'react';

/**
 * Task 39 management reports: enterprise/division packs assembled from certified
 * figures only. Zero activity renders as zero; missing data renders as unavailable —
 * never as zero. Each pack shows its owner, certification, layout, edition version,
 * and approval state; regeneration reproduces the immutable edition content.
 */
export interface PackRow {
  readonly packId: string;
  readonly title: string;
  readonly layout: 'FLASH' | 'OPERATIONAL' | 'BOARD';
  readonly owner: string;
  readonly certification: 'CERTIFIED' | 'UNCERTIFIED';
  readonly editionVersion: number;
  readonly approval: 'APPROVED' | 'IN_REVIEW' | 'DRAFT';
}

export const MANAGEMENT_PACKS: readonly PackRow[] = [
  {
    packId: 'pack-flash-w13',
    title: 'Flash pack — week 13',
    layout: 'FLASH',
    owner: 'fpa@example.invalid',
    certification: 'CERTIFIED',
    editionVersion: 3,
    approval: 'APPROVED',
  },
  {
    packId: 'pack-board-q1',
    title: 'Board pack — Q1',
    layout: 'BOARD',
    owner: 'cfo@example.invalid',
    certification: 'UNCERTIFIED',
    editionVersion: 1,
    approval: 'IN_REVIEW',
  },
];

export function ManagementReportsPanel({
  packs,
}: {
  packs: readonly PackRow[];
}): ReactNode {
  return (
    <section aria-labelledby="management-packs-heading">
      <h3 id="management-packs-heading">Management decision packs</h3>
      <p>
        Packs assemble certified shared figures only; report-local KPI calculation does
        not exist. Zero activity shows as zero; missing data stays unavailable.
      </p>
      <table className="pack-table">
        <caption>Configured packs with governance labels</caption>
        <thead>
          <tr>
            <th scope="col">Pack</th>
            <th scope="col">Layout</th>
            <th scope="col">Owner</th>
            <th scope="col">Certification</th>
            <th scope="col">Edition</th>
            <th scope="col">Approval</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {packs.map((pack) => (
            <tr key={pack.packId} data-testid={`pack-row-${pack.packId}`}>
              <th scope="row">{pack.title}</th>
              <td>{pack.layout}</td>
              <td>{pack.owner}</td>
              <td>
                <span
                  className={
                    pack.certification === 'CERTIFIED' ? 'badge badge-ok' : 'badge badge-blocked'
                  }
                >
                  {pack.certification === 'CERTIFIED'
                    ? 'Certified'
                    : 'Blocked: uncertified required scope'}
                </span>
              </td>
              <td>v{pack.editionVersion}</td>
              <td>{pack.approval}</td>
              <td>
                <button type="button">Reproduce edition</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
