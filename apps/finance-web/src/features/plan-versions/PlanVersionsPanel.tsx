import type { ReactNode } from 'react';

/**
 * Task 41 plan versions: copy/edit/submit/approve/lock/compare/reopen journeys with
 * visible source, version, revision, and lock provenance. Locked versions show as
 * immutable; reopening is a named-human, reason-bearing act.
 */
export interface PlanVersionRow {
  readonly planId: string;
  readonly name: string;
  readonly type: 'BUDGET' | 'FORECAST';
  readonly revision: number;
  readonly status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'LOCKED' | 'SUPERSEDED';
  readonly seedSource: string;
  readonly lockProvenance: string | null;
}

export const PLAN_VERSIONS: readonly PlanVersionRow[] = [
  {
    planId: 'plan-bud-2027',
    name: 'FY2027 Budget (reopen r2)',
    type: 'BUDGET',
    revision: 2,
    status: 'DRAFT',
    seedSource: 'PRIOR_PLAN plan-bud-2027:r1',
    lockProvenance: null,
  },
  {
    planId: 'plan-bud-2027',
    name: 'FY2027 Budget',
    type: 'BUDGET',
    revision: 1,
    status: 'SUPERSEDED',
    seedSource: 'ACTUALS actuals-certified-2026',
    lockProvenance: 'locked@superseded-r1',
  },
  {
    planId: 'plan-fc-2026',
    name: 'Rolling forecast Q4',
    type: 'FORECAST',
    revision: 3,
    status: 'LOCKED',
    seedSource: 'PRIOR_PLAN plan-fc-2026:r2',
    lockProvenance: 'locked@current-r3',
  },
];

export function PlanVersionsPanel({
  versions,
}: {
  versions: readonly PlanVersionRow[];
}): ReactNode {
  return (
    <section aria-labelledby="plan-versions-heading">
      <h3 id="plan-versions-heading">Plan versions</h3>
      <p>
        Versions are immutable once locked; reopening requires a nonblank reason, scope,
        and approval, and creates a new revision — the prior approved version is never
        edited.
      </p>
      <table className="pack-table">
        <caption>Plan versions with lineage and lock provenance</caption>
        <thead>
          <tr>
            <th scope="col">Plan</th>
            <th scope="col">Type</th>
            <th scope="col">Revision</th>
            <th scope="col">Status</th>
            <th scope="col">Seed source</th>
            <th scope="col">Lock provenance</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {versions.map((v) => (
            <tr
              key={`${v.planId}-r${String(v.revision)}`}
              data-testid={`plan-row-${v.planId}-r${String(v.revision)}`}
            >
              <th scope="row">{v.name}</th>
              <td>{v.type}</td>
              <td>r{v.revision}</td>
              <td>
                <span
                  className={
                    v.status === 'LOCKED' || v.status === 'SUPERSEDED'
                      ? 'badge badge-ok'
                      : 'badge badge-blocked'
                  }
                >
                  {v.status === 'LOCKED'
                    ? 'Locked (immutable)'
                    : v.status === 'SUPERSEDED'
                      ? 'Superseded (preserved)'
                      : v.status.toLowerCase()}
                </span>
              </td>
              <td>{v.seedSource}</td>
              <td>{v.lockProvenance ?? '—'}</td>
              <td>
                {v.status === 'DRAFT' ? (
                  <>
                    <button type="button">Edit</button>{' '}
                    <button type="button">Submit</button>
                  </>
                ) : v.status === 'LOCKED' ? (
                  <button type="button">Request reopen</button>
                ) : (
                  <span>Read-only</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
