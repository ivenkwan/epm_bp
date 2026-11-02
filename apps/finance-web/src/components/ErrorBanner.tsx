import type { ReactNode } from 'react';

/**
 * Correlation-aware error banner. When a problem+json payload is present the stable code
 * and correlation id are shown so support can trace the exact request; internals never
 * reach the UI.
 */
export function ErrorBanner({
  message,
  correlationId,
  code,
  onDismiss,
}: {
  message: string;
  correlationId?: string;
  code?: string;
  onDismiss?: () => void;
}): ReactNode {
  return (
    <div role="alert" className="error-banner" aria-live="polite">
      <p className="error-message">{message}</p>
      {code !== undefined ? <p className="error-code">Code: {code}</p> : null}
      {correlationId !== undefined ? (
        <p className="error-correlation">Correlation: {correlationId}</p>
      ) : null}
      {onDismiss !== undefined ? (
        <button type="button" className="error-dismiss" onClick={onDismiss}>
          Dismiss
        </button>
      ) : null}
    </div>
  );
}
