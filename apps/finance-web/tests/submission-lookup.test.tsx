import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { SubmissionLookup } from '../src/app/SubmissionLookup';
import { ApiError, ProblemSchema, SubmissionAckSchema } from '../src/api/client';
import type { SubmissionAck } from '../src/api/client';

function mockFetch(status: number, body: unknown, headers: Record<string, string> = {}) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json', ...headers },
      }),
  );
}

const validAck: SubmissionAck = {
  submissionId: 'sub-1',
  revision: 2,
  status: 'RECEIVED',
  recordedAt: '2026-10-31T01:15:22Z',
  etag: '2',
};

describe('runtime boundary validation', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('accepts a payload that matches the generated contract', () => {
    const parsed = SubmissionAckSchema.safeParse(validAck);
    expect(parsed.success).toBe(true);
  });

  it('rejects an invalid API payload instead of rendering it', async () => {
    mockFetch(200, { submissionId: 42, revision: 'two' });
    render(<SubmissionLookup onError={() => undefined} />);
    await userEvent.type(screen.getByLabelText('Submission identifier'), 'sub-1');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));
    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
    expect(screen.getByText('The submission could not be loaded.')).toBeInTheDocument();
  });

  it('parses problem+json into the stable contract shape', () => {
    const problem = {
      type: 'about:blank',
      title: 'Stale revision',
      status: 409,
      code: 'STALE_REVISION',
      correlationId: 'corr-1',
    };
    const parsed = ProblemSchema.safeParse(problem);
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.code).toBe('STALE_REVISION');
    }
  });
});

describe('SubmissionLookup states', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows loading, then the governed submission with preserved decimals', async () => {
    mockFetch(200, validAck, { ETag: '"revision-2"' });
    render(<SubmissionLookup onError={() => undefined} />);
    await userEvent.type(screen.getByLabelText('Submission identifier'), 'sub-1');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));
    await waitFor(() => {
      expect(screen.getByText('Submission sub-1')).toBeInTheDocument();
    });
    expect(screen.getByText('Status:')).toBeInTheDocument();
    expect(screen.getByText('RECEIVED')).toBeInTheDocument();
    const money = screen.getByTitle('1250000.00 USD');
    expect(money).toHaveAttribute('data-raw', '1250000.00');
  });

  it('shows the empty state for a missing submission', async () => {
    mockFetch(404, {
      title: 'Submission not found',
      status: 404,
      correlationId: 'corr-404',
    }, { 'Content-Type': 'application/problem+json' });
    render(<SubmissionLookup onError={() => undefined} />);
    await userEvent.type(screen.getByLabelText('Submission identifier'), 'sub-missing');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));
    await waitFor(() => {
      expect(
          screen.getByText('No submission exists with that identifier.'),
      ).toBeInTheDocument();
    });
  });

  it('shows a correlation-aware error state for server failures', async () => {
    mockFetch(409, {
      title: 'Stale revision',
      status: 409,
      code: 'STALE_REVISION',
      correlationId: 'corr-409',
    }, { 'Content-Type': 'application/problem+json', 'X-Correlation-Id': 'corr-409' });
    render(<SubmissionLookup onError={() => undefined} />);
    await userEvent.type(screen.getByLabelText('Submission identifier'), 'sub-stale');
    await userEvent.click(screen.getByRole('button', { name: 'Look up' }));
    await waitFor(() => {
      expect(screen.getByText('Stale revision')).toBeInTheDocument();
    });
    expect(screen.getByText('Code: STALE_REVISION')).toBeInTheDocument();
    expect(screen.getByText('Correlation: corr-409')).toBeInTheDocument();
  });

  it('keeps ApiError correlated for unsupported payloads', () => {
    const error = new ApiError(500, null, 'corr-x');
    expect(error.message).toContain('500');
    expect(error.correlationId).toBe('corr-x');
  });
});
