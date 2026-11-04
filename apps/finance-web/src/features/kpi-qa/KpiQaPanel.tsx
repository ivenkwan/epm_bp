import type { ReactNode } from 'react';

/**
 * Task 53 bounded KPI Q&A surface: with the route disabled (the shipped state), the
 * certified dashboard and drill views are the deterministic alternative; when a
 * signed pilot enables the route, answers carry basis + citations or safe refusals.
 */
export const QA_ROUTE_STATE = {
  route: 'kpi-qa (Tier 2)',
  enabled: false,
  alternative: 'Certified dashboard and drill views',
} as const;

export function KpiQaPanel({ state }: { state: typeof QA_ROUTE_STATE }): ReactNode {
  return (
    <section aria-labelledby="kpi-qa-heading">
      <h3 id="kpi-qa-heading">KPI Q&amp;A</h3>
      <p>
        Route <strong>{state.route}</strong>:{' '}
        <span className={state.enabled ? 'badge badge-ok' : 'badge badge-blocked'}>
          {state.enabled ? 'Enabled (signed pilot)' : 'Disabled — safe refusal'}
        </span>{' '}
        Deterministic alternative: {state.alternative}. When enabled, answers cite
        their KPI/version/period basis and evidence — or refuse safely; values are
        never invented.
      </p>
    </section>
  );
}
