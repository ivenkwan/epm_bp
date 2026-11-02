import type { ReactNode } from 'react';

/**
 * Exception-case views (Task 14): triage/assignment/resolution/verification/closure
 * journeys over server-owned state. Timers only remind/escalate; every consequential
 * action is a named human decision enforced server-side.
 */
export function CasesPanel(): ReactNode {
  return (
    <section aria-labelledby="cases-heading">
      <h2 id="cases-heading">Exception cases</h2>
      <p>
        Durable cases with independent approval and verification, immutable transition
        history, and advisory timers that never approve or close anything.
      </p>
    </section>
  );
}
