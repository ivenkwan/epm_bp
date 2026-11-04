import type { ReactNode } from 'react';

/**
 * Task 54 forecast challenger surface: side-by-side baseline/challenger and
 * anomaly-review views. Recommendations never modify baselines; humans
 * accept/dismiss/annotate/escalate; insufficient evidence presents no alternative.
 */
export interface ChallengerRow {
  readonly finding: string;
  readonly confidence: string;
  readonly evidence: string;
  readonly status: 'NO_ANOMALY' | 'INSUFFICIENT_EVIDENCE' | 'PROPOSED' | 'ACCEPTED';
}

export const CHALLENGER_ROWS: readonly ChallengerRow[] = [
  {
    finding: 'Baseline P5 deviates 200.00 from certified history mean',
    confidence: '0.95',
    evidence: 'history=5 periods; baseline=P5=800.00',
    status: 'PROPOSED',
  },
  {
    finding: 'Newly onboarded entity: 2 observations only',
    confidence: '—',
    evidence: 'INSUFFICIENT_EVIDENCE — deterministic forecast stands',
    status: 'INSUFFICIENT_EVIDENCE',
  },
];

export function ForecastChallengerPanel({ rows }: { rows: readonly ChallengerRow[] }): ReactNode {
  return (
    <section aria-labelledby="challenger-heading">
      <h3 id="challenger-heading">Forecast challenger</h3>
      <p>
        Ranked recommendations carry rationale, confidence, and comparison evidence;
        insufficient history presents <strong>no</strong> alternative. Accepting a
        recommendation creates a separate governed scenario version — baselines and
        certifications are never modified automatically.
      </p>
      <table className="pack-table">
        <caption>Anomaly review with human decisions</caption>
        <thead>
          <tr>
            <th scope="col">Finding</th>
            <th scope="col">Confidence</th>
            <th scope="col">Evidence</th>
            <th scope="col">Status</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.finding}>
              <th scope="row">{row.finding}</th>
              <td>{row.confidence}</td>
              <td>{row.evidence}</td>
              <td>
                <span
                  className={
                    row.status === 'ACCEPTED' ? 'badge badge-ok' : 'badge badge-blocked'
                  }
                >
                  {row.status === 'INSUFFICIENT_EVIDENCE'
                    ? 'Insufficient evidence — no alternative'
                    : row.status === 'NO_ANOMALY'
                      ? 'No anomaly'
                      : row.status}
                </span>
              </td>
              <td>
                {row.status === 'PROPOSED' ? (
                  <>
                    <button type="button">Accept</button>{' '}
                    <button type="button">Dismiss</button>{' '}
                    <button type="button">Annotate</button>{' '}
                    <button type="button">Escalate</button>
                  </>
                ) : (
                  '—'
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
