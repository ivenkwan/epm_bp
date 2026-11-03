import { useEffect, useMemo, useState } from 'react';

/** Session lifecycle states shared across the shell. */
export enum SessionState {
  Anonymous = 'ANONYMOUS',
  Active = 'ACTIVE',
  Expired = 'EXPIRED',
}

const SESSION_TIMEOUT_MS = 30 * 60 * 1000;

/**
 * R0 session model: the OIDC handoff lands with Task 8; until then the shell models the
 * lifecycle contract - anonymous until configured, expiry handled without silent reuse -
 * so the browser never assumes entitlements from session state alone.
 */
export function useSession(configuredActor: string | undefined = undefined): {
  state: SessionState;
  actor: string;
} {
  const actor = configuredActor ?? '';
  const [state, setState] = useState<SessionState>(
    actor === '' ? SessionState.Anonymous : SessionState.Active,
  );

  useEffect(() => {
    if (state !== SessionState.Active) {
      return;
    }
    const timer = window.setTimeout(() => {
      setState(SessionState.Expired);
    }, SESSION_TIMEOUT_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [state]);

  return useMemo(() => ({ state, actor }), [state, actor]);
}

/** Formats an RFC-3339 UTC timestamp for display without shifting its meaning. */
export function formatRecordedAt(recordedAtUtc: string, locale: string): string {
  const parsed = new Date(recordedAtUtc);
  if (Number.isNaN(parsed.getTime())) {
    return recordedAtUtc;
  }
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'UTC',
  }).format(parsed);
}
