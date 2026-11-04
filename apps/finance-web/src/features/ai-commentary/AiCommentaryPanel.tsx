import type { ReactNode } from 'react';

/**
 * Task 52 AI commentary pilot surface: with the route disabled (the shipped state)
 * the deterministic fallback is authoritative; AI drafts, when a signed pilot
 * enables them, stay unpublished until independent human review.
 */
export interface PilotStatus {
  readonly route: string;
  readonly state: 'DISABLED' | 'ENABLED';
  readonly fallback: string;
}

export const PILOT_STATUS: readonly PilotStatus[] = [
  { route: 'commentary-draft (Tier 1)', state: 'DISABLED', fallback: 'Deterministic commentary — always available' },
];

export function AiCommentaryPanel({ status }: { status: readonly PilotStatus[] }): ReactNode {
  return (
    <section aria-labelledby="ai-commentary-heading">
      <h3 id="ai-commentary-heading">AI commentary pilot</h3>
      <p>
        Generative routes ship disabled; deterministic commentary is the complete
        fallback. When a signed pilot enables a route, its drafts carry full
        provenance and stay unpublished until an independent human reviewer publishes
        — rejected or invalid AI text never appears in published editions.
      </p>
      <table className="pack-table">
        <caption>Pilot route status</caption>
        <thead>
          <tr>
            <th scope="col">Route</th>
            <th scope="col">State</th>
            <th scope="col">Fallback</th>
          </tr>
        </thead>
        <tbody>
          {status.map((row) => (
            <tr key={row.route}>
              <th scope="row">{row.route}</th>
              <td>
                <span className={row.state === 'ENABLED' ? 'badge badge-ok' : 'badge badge-blocked'}>
                  {row.state === 'ENABLED' ? 'Enabled (signed pilot)' : 'Disabled — safe refusal'}
                </span>
              </td>
              <td>{row.fallback}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
