import type { ReactNode } from 'react';

/**
 * Task 48 what-if scenarios: driver/override, sensitivity, comparison, and
 * calculation-trace views. Overrides stay in approved ranges; recalculation is
 * server-side and deterministic; nothing changes plans or actuals automatically.
 */
export interface ScenarioRow {
  readonly scenarioId: string;
  readonly name: string;
  readonly overrides: readonly string[];
  readonly status: 'DRAFT' | 'VALID' | 'INVALID' | 'PROMOTED';
  readonly deltaVsBaseline: string;
}

export const SCENARIOS: readonly ScenarioRow[] = [
  {
    scenarioId: 'sc-1',
    name: 'Volume +10%, FX +5%',
    overrides: ['revenue VOLUME 1.10', 'fx FX 1.05'],
    status: 'VALID',
    deltaVsBaseline: '+100.00 revenue',
  },
  {
    scenarioId: 'sc-2',
    name: 'Rejected out-of-range probe',
    overrides: ['fx FX 9.99 (refused)'],
    status: 'DRAFT',
    deltaVsBaseline: '—',
  },
];

export function ScenariosPanel({ scenarios }: { scenarios: readonly ScenarioRow[] }): ReactNode {
  return (
    <section aria-labelledby="scenarios-heading">
      <h3 id="scenarios-heading">What-if scenarios</h3>
      <p>
        Scenarios are isolated copies of a locked baseline. Overrides must stay inside
        approved driver ranges; circular or unbounded models are refused; promotion
        creates a new governed version — baselines and prior versions never change
        in place.
      </p>
      <table className="pack-table">
        <caption>Scenarios with overrides, status, and baseline deltas</caption>
        <thead>
          <tr>
            <th scope="col">Scenario</th>
            <th scope="col">Overrides</th>
            <th scope="col">Status</th>
            <th scope="col">Δ vs baseline</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {scenarios.map((scenario) => (
            <tr key={scenario.scenarioId} data-testid={`scenario-${scenario.scenarioId}`}>
              <th scope="row">{scenario.name}</th>
              <td>{scenario.overrides.join('; ')}</td>
              <td>
                <span
                  className={
                    scenario.status === 'VALID' || scenario.status === 'PROMOTED'
                      ? 'badge badge-ok'
                      : 'badge badge-blocked'
                  }
                >
                  {scenario.status === 'VALID'
                    ? 'Valid (calculable)'
                    : scenario.status === 'PROMOTED'
                      ? 'Promoted as new version'
                      : 'Draft'}
                </span>
              </td>
              <td>{scenario.deltaVsBaseline}</td>
              <td>
                <button type="button">Trace</button> <button type="button">Compare</button>{' '}
                {scenario.status === 'VALID' ? <button type="button">Promote</button> : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
