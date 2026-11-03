import { useState } from 'react';
import type { ReactNode } from 'react';

/**
 * Optional stock/unit liquidity context (Task 36): renders only when the feature is
 * enabled. Zero renders as zero; blank optional remarks stay blank; every row carries
 * source revision and freshness labels; the certified cash position is never touched
 * by this view.
 */
export function LiquidityContextPanel({ enabled }: { enabled: boolean }): ReactNode {
  const [note] = useState(
    'Stock/unit context renders contracted, certified submissions only.',
  );
  if (!enabled) {
    return (
      <section aria-labelledby="liquidity-heading">
        <h2 id="liquidity-heading">Stock &amp; unit context</h2>
        <p role="status">Disabled pending a named business owner&apos;s activation decision.</p>
      </section>
    );
  }
  return (
    <section aria-labelledby="liquidity-heading">
      <h2 id="liquidity-heading">Stock &amp; unit context</h2>
      <p>{note}</p>
      <p>Zero quantities display as zero; missing detail stays unavailable.</p>
    </section>
  );
}
