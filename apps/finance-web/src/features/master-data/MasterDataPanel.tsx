import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';

/**
 * Master-data steward shell (Task 9): server-driven propose/compare/activate view.
 * The browser renders governed state; interval and cycle logic stay server-side.
 */
export function MasterDataPanel(): ReactNode {
  const [message, setMessage] = useState<string>('Master data steward journeys arrive with the admin API wiring.');

  useEffect(() => {
    document.title = 'EPM Finance — Master data';
    setMessage((current: string): string => current);
  }, []);

  return (
    <section aria-labelledby="master-data-heading">
      <h2 id="master-data-heading">Master data</h2>
      <p role="status">{message}</p>
      <p>Effective-dated versions, hierarchy activation, and rollback are governed by the finance-admin service.</p>
    </section>
  );
}
