import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { SessionState, useSession } from '../app/session';
import { SubmissionLookup } from '../app/SubmissionLookup';
import { ErrorBanner } from '../components/ErrorBanner';

/**
 * Accessible application shell: skip link, landmark structure, keyboard-operable
 * navigation, and visible certification/freshness/status slots. The browser never owns
 * entitlements or financial formulas; every protected journey is server-driven.
 */
export function AppShell(): ReactNode {
  const session = useSession();
  const [active, setActive] = useState<'submissions' | 'reports'>('submissions');
  const [error, setError] = useState<string | null>(null);
  const mainRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (error !== null) {
      mainRef.current?.focus();
    }
  }, [error]);

  return (
    <div className="shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <header className="shell-header">
        <p className="shell-title">EPM Finance</p>
        <nav aria-label="Primary">
          <ul className="shell-nav">
            <li>
              <button
                type="button"
                aria-current={active === 'submissions' ? 'page' : undefined}
                className={active === 'submissions' ? 'active' : undefined}
                onClick={() => {
                  setActive('submissions');
                }}
              >
                Submissions
              </button>
            </li>
            <li>
              <button
                type="button"
                aria-current={active === 'reports' ? 'page' : undefined}
                className={active === 'reports' ? 'active' : undefined}
                onClick={() => {
                  setActive('reports');
                }}
              >
                Reports
              </button>
            </li>
          </ul>
        </nav>
        <SessionBadge session={session} />
      </header>
      {error !== null ? (
        <ErrorBanner
          message={error}
          onDismiss={() => {
            setError(null);
          }}
        />
      ) : null}
      <main id="main-content" ref={mainRef} tabIndex={-1} aria-label="Main content">
        {session.state === SessionState.Expired ? (
          <ErrorBanner
            message="Your session expired. Please sign in again to continue."
            onDismiss={() => {
            setError('session-expired-acknowledged');
          }}
          />
        ) : null}
        {active === 'submissions' ? (
          <SubmissionLookup onError={setError} />
        ) : (
          <section aria-labelledby="reports-heading">
            <h2 id="reports-heading">Reports</h2>
            <p>
              Certified packs appear here once reporting (Task 28) is delivered. Every
              published edition will show its certification, freshness, and version.
            </p>
            <StatusSlot label="Certification" value="Not yet available" />
            <StatusSlot label="Freshness" value="Not yet available" />
          </section>
        )}
      </main>
      <footer className="shell-footer">
        <p>Synthetic environment. All figures are for demonstration only.</p>
      </footer>
    </div>
  );
}

function SessionBadge({ session }: { session: { state: SessionState; actor: string } }): ReactNode {
  return (
    <p className="session-badge" role="status">
      {session.state === SessionState.Anonymous ? 'Not signed in' : `Signed in: ${session.actor}`}
    </p>
  );
}

/** Visible governance slot used by certified products (certification/freshness/status). */
export function StatusSlot({ label, value }: { label: string; value: string }): ReactNode {
  return (
    <p className="status-slot">
      <span className="status-label">{label}:</span> <span className="status-value">{value}</span>
    </p>
  );
}
