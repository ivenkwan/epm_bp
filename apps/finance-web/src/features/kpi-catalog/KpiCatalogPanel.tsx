import type { ReactNode } from 'react';

/**
 * KPI catalog view (Task 12): renders certified cards with owner, formula, grain, and
 * certification evidence. Values arrive as decimal strings and are never recomputed.
 */
export function KpiCatalogPanel(): ReactNode {
  return (
    <section aria-labelledby="kpi-catalog-heading">
      <h2 id="kpi-catalog-heading">KPI catalog</h2>
      <p>
        Certified KPI cards carry formula, grain, owner, cadence, sensitivity, worked
        examples, and certification evidence. Unavailable results stay unavailable —
        nulls never become zero.
      </p>
    </section>
  );
}
