import type { ReactNode } from 'react';

/**
 * Source contract registration surface (Task 10): proposes, compares, and activates
 * versioned contracts. All lifecycle logic is server-side; this panel renders state.
 */
export function SourceContractsPanel(): ReactNode {
  return (
    <section aria-labelledby="source-contracts-heading">
      <h2 id="source-contracts-heading">Source contracts</h2>
      <p>
        Versioned contracts with independent approval, precedence-resolved authority, and
        immutable transition evidence live in the finance-integration service.
      </p>
    </section>
  );
}
