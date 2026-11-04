import type { ReactNode } from 'react';

/**
 * Task 45 recurring reporting workspace: weekly/monthly refresh, commentary, review,
 * publication, supersession, and governed distribution in one role-based surface —
 * with no automatic publication anywhere.
 */
export interface WorkspaceEdition {
  readonly editionId: string;
  readonly cadence: 'WEEKLY' | 'MONTHLY';
  readonly status: 'IN_REVIEW' | 'PUBLISHED' | 'SUPERSEDED';
  readonly publishedBy: string | null;
  readonly retention: string;
  readonly exportFormats: readonly string[];
}

export const WORKSPACE_EDITIONS: readonly WorkspaceEdition[] = [
  {
    editionId: 'ed-w45',
    cadence: 'WEEKLY',
    status: 'IN_REVIEW',
    publishedBy: null,
    retention: 'RETENTION_7Y',
    exportFormats: ['Excel', 'PDF', 'Presentation'],
  },
  {
    editionId: 'ed-m10',
    cadence: 'MONTHLY',
    status: 'PUBLISHED',
    publishedBy: 'publisher@example.invalid',
    retention: 'RETENTION_7Y',
    exportFormats: ['Excel', 'PDF', 'Presentation'],
  },
];

export function ReportingWorkspacePanel({
  editions,
}: {
  editions: readonly WorkspaceEdition[];
}): ReactNode {
  return (
    <section aria-labelledby="workspace-heading">
      <h3 id="workspace-heading">Recurring reporting workspace</h3>
      <p>
        Refresh produces new editions for review; publication and supersession are
        named-human acts — nothing publishes automatically. Exports are watermarked,
        retention-classified, and re-authorize every recipient at delivery.
      </p>
      <table className="pack-table">
        <caption>Recurring editions with publication provenance</caption>
        <thead>
          <tr>
            <th scope="col">Edition</th>
            <th scope="col">Cadence</th>
            <th scope="col">Status</th>
            <th scope="col">Published by</th>
            <th scope="col">Retention</th>
            <th scope="col">Export</th>
          </tr>
        </thead>
        <tbody>
          {editions.map((edition) => (
            <tr key={edition.editionId} data-testid={`edition-${edition.editionId}`}>
              <th scope="row">{edition.editionId}</th>
              <td>{edition.cadence}</td>
              <td>
                <span
                  className={
                    edition.status === 'PUBLISHED' ? 'badge badge-ok' : 'badge badge-blocked'
                  }
                >
                  {edition.status === 'IN_REVIEW'
                    ? 'In review — awaiting named publication'
                    : edition.status}
                </span>
              </td>
              <td>{edition.publishedBy ?? '—'}</td>
              <td>{edition.retention}</td>
              <td>
                {edition.exportFormats.map((format) => (
                  <button key={format} type="button">
                    {format}
                  </button>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
