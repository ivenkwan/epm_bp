import type { ReactNode } from 'react';

/**
 * Task 49 allocations: model configuration, simulation difference, run, reversal,
 * and trace-path views with explicit totals and rounding residuals. Step totals are
 * preserved exactly; reversal is a linked compensating run.
 */
export interface AllocationRunRow {
  readonly runId: string;
  readonly model: string;
  readonly steps: number;
  readonly allocatedTotal: string;
  readonly status: 'EXECUTED' | 'REVERSED';
  readonly residualRule: string;
}

export const ALLOCATION_RUNS: readonly AllocationRunRow[] = [
  {
    runId: 'run-1',
    model: 'alloc-1 (IT step-down)',
    steps: 2,
    allocatedTotal: '320.00',
    status: 'EXECUTED',
    residualRule: 'LARGEST_REMAINDER',
  },
  {
    runId: 'run-1-rev',
    model: 'alloc-1 reversal',
    steps: 2,
    allocatedTotal: '-320.00',
    status: 'EXECUTED',
    residualRule: 'LARGEST_REMAINDER',
  },
];

export function AllocationsPanel({ runs }: { runs: readonly AllocationRunRow[] }): ReactNode {
  return (
    <section aria-labelledby="allocations-heading">
      <h3 id="allocations-heading">Allocations</h3>
      <p>
        Steps run in approved sequence; each preserves its allocated total exactly with
        largest-remainder residuals; step-down excludes earlier senders; every entry
        traces source pool to receiver; reversal is a linked compensating run that
        never overwrites the ledger.
      </p>
      <table className="pack-table">
        <caption>Allocation runs with totals and rounding rule</caption>
        <thead>
          <tr>
            <th scope="col">Run</th>
            <th scope="col">Model</th>
            <th scope="col">Steps</th>
            <th scope="col">Allocated total</th>
            <th scope="col">Residual rule</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.runId} data-testid={`alloc-run-${run.runId}`}>
              <th scope="row">{run.runId}</th>
              <td>{run.model}</td>
              <td>{run.steps}</td>
              <td>{run.allocatedTotal}</td>
              <td>{run.residualRule}</td>
              <td>
                <button type="button">Trace path</button>{' '}
                {run.status === 'EXECUTED' && !run.runId.endsWith('-rev') ? (
                  <button type="button">Reverse</button>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
