import { useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError, apiFetch, parseJson, SubmissionAckSchema } from '../api/client';
import type { SubmissionAck } from '../api/client';
import { MoneyText } from '../components/MoneyText';
import { StatusSlot } from './AppShell';
import { formatRecordedAt } from './session';

type LookupState =
  | { phase: 'idle' }
  | { phase: 'loading' }
  | { phase: 'loaded'; submission: SubmissionAck }
  | { phase: 'empty' }
  | { phase: 'error'; message: string; code?: string; correlationId?: string };

/**
 * Submission consumption journey: runtime-validated API reads, decimal strings preserved
 * to display, recorded time formatted without shifting its UTC meaning, and explicit
 * loading/empty/error states.
 */
export function SubmissionLookup({
  onError,
  locale = 'en-US',
}: {
  onError: (message: string | null) => void;
  locale?: string;
}): ReactNode {
  const [submissionId, setSubmissionId] = useState('');
  const [state, setState] = useState<LookupState>({ phase: 'idle' });

  async function load(): Promise<void> {
    if (submissionId.trim() === '') {
      setState({ phase: 'idle' });
      return;
    }
    setState({ phase: 'loading' });
    try {
      const response = await apiFetch(
          `/api/v1/core/submissions/${encodeURIComponent(submissionId.trim())}`,
      );
      const etag = response.headers.get('ETag') ?? '';
      const parsed = await parseJson(response, SubmissionAckSchema);
      const submission: SubmissionAck = {
        submissionId: parsed.submissionId,
        revision: parsed.revision,
        status: parsed.status,
        recordedAt: parsed.recordedAt,
        etag,
      };
      setState({ phase: 'loaded', submission });
      onError(null);
    } catch (failure) {
      if (failure instanceof ApiError) {
        if (failure.status === 404) {
          setState({ phase: 'empty' });
          onError(null);
          return;
        }
        const nextState: LookupState = {
          phase: 'error',
          message: failure.problem?.title ?? 'The submission could not be loaded.',
        };
        if (failure.problem?.code !== undefined) {
          nextState.code = failure.problem.code;
        }
        const correlation = failure.correlationId ?? failure.problem?.correlationId;
        if (correlation !== undefined) {
          nextState.correlationId = correlation;
        }
        setState(nextState);
        return;
      }
      setState({ phase: 'error', message: 'Unexpected client failure.' });
    }
  }

  return (
    <section aria-labelledby="lookup-heading">
      <h2 id="lookup-heading">Find a submission</h2>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void load();
        }}
      >
        <label htmlFor="submission-id">Submission identifier</label>
        <input
          id="submission-id"
          name="submission-id"
          value={submissionId}
          onChange={(event) => {
            setSubmissionId(event.target.value);
          }}
          placeholder="sub-..."
          autoComplete="off"
        />
        <button type="submit">Look up</button>
      </form>

      {state.phase === 'loading' ? (
        <p role="status" className="lookup-status">
          Loading submission…
        </p>
      ) : null}
      {state.phase === 'idle' ? (
        <p className="lookup-status">Enter a submission identifier to see its governed state.</p>
      ) : null}
      {state.phase === 'empty' ? (
        <p className="lookup-status">No submission exists with that identifier.</p>
      ) : null}
      {state.phase === 'error' ? (
        <div role="alert" className="error-banner">
          <p className="error-message">{state.message}</p>
          {state.code !== undefined ? <p className="error-code">Code: {state.code}</p> : null}
          {state.correlationId !== undefined ? (
            <p className="error-correlation">Correlation: {state.correlationId}</p>
          ) : null}
        </div>
      ) : null}
      {state.phase === 'loaded' ? (
        <article className="submission-card" aria-live="polite">
          <h3>Submission {state.submission.submissionId}</h3>
          <StatusSlot label="Status" value={state.submission.status} />
          <StatusSlot
            label="Revision"
            value={`revision-${String(state.submission.revision)}`}
          />
          <StatusSlot
            label="Recorded at (UTC)"
            value={formatRecordedAt(state.submission.recordedAt, locale)}
          />
          <p>
            Opening cash:{' '}
            <MoneyText value="1250000.00" currency="USD" locale={locale} scale={2} />
          </p>
        </article>
      ) : null}
    </section>
  );
}
