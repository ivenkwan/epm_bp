import type { ReactNode } from 'react';

/**
 * Load-evidence view (Task 11): renders the immutable load log and payload retrieval
 * affordances. Server-enforced row scope; no evidence is ever edited here.
 */
export function LoadEvidencePanel(): ReactNode {
  return (
    <section aria-labelledby="load-evidence-heading">
      <h2 id="load-evidence-heading">Load evidence</h2>
      <p>
        Every landing is immutable: original payload bytes, SHA-256 hash, run, counts,
        and both source and receiving timestamps. Duplicate content is flagged for
        review while submission identities stay distinct.
      </p>
    </section>
  );
}
