import type { ReactNode } from 'react';

/**
 * Task 57 mapping studio: business users maintain effective-dated mapping tables
 * (including chart of accounts), accounts, and group mappings visually — timelines,
 * old/new compare, impact preview — as governed configuration, never code. Every
 * change appends a version; history is never lost.
 */
export interface MappingVersionRow {
  readonly table: string;
  readonly version: number;
  readonly effective: string;
  readonly status: 'ACTIVE' | 'PROPOSED' | 'SUPERSEDED';
  readonly provenance: string;
}

export const MAPPING_VERSIONS: readonly MappingVersionRow[] = [
  {
    table: 'Chart of accounts',
    version: 2,
    effective: '2027-01-01 → ∞',
    status: 'PROPOSED',
    provenance: 'proposed by steward@epm — awaiting independent approval',
  },
  {
    table: 'Chart of accounts',
    version: 1,
    effective: '2026-01-01 → 2027-01-01',
    status: 'ACTIVE',
    provenance: 'activated by approver@epm',
  },
];

export function MappingStudioPanel({
  versions,
}: {
  versions: readonly MappingVersionRow[];
}): ReactNode {
  return (
    <section aria-labelledby="mapping-studio-heading">
      <h3 id="mapping-studio-heading">Mapping studio</h3>
      <p>
        Business users maintain mapping tables, chart of accounts, accounts, and group
        mappings on effective-dated timelines — governed configuration, never
        vendor-side code changes. Overlaps, duplicates, and group violations refuse
        with actionable errors; rollback is a new version; history is never lost.
      </p>
      <table className="pack-table">
        <caption>Mapping versions with provenance</caption>
        <thead>
          <tr>
            <th scope="col">Table</th>
            <th scope="col">Version</th>
            <th scope="col">Effective</th>
            <th scope="col">Status</th>
            <th scope="col">Provenance</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {versions.map((v) => (
            <tr key={`${v.table}-v${String(v.version)}`}>
              <th scope="row">{v.table}</th>
              <td>v{v.version}</td>
              <td>{v.effective}</td>
              <td>
                <span className={v.status === 'ACTIVE' ? 'badge badge-ok' : 'badge badge-blocked'}>
                  {v.status === 'PROPOSED' ? 'Proposed — awaiting approval' : v.status}
                </span>
              </td>
              <td>{v.provenance}</td>
              <td>
                <button type="button">Compare</button> <button type="button">Timeline</button>{' '}
                <button type="button">Preview impact</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
