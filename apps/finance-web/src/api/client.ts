import { z } from 'zod';
import type { components } from './generated/common-v1';

/** Generated problem shape from the common OpenAPI contract. */
export type Problem = components['schemas']['Problem'];

/** Runtime-validated problem shape (zod-inferred; structurally the generated type). */
export const ProblemSchema = z.object({
  type: z.string().optional(),
  title: z.string(),
  status: z.number(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  code: z.string().optional(),
  correlationId: z.string(),
  violations: z
    .array(
      z.object({
        field: z.string(),
        code: z.string(),
        message: z.string(),
      }),
    )
    .optional(),
});

export type ProblemRuntime = z.infer<typeof ProblemSchema>;

/** Submission acknowledgement from the R0 API-safety surface. */
export interface SubmissionAck {
  submissionId: string;
  revision: number;
  status: string;
  recordedAt: string;
  etag: string;
}

export const SubmissionAckSchema = z.object({
  submissionId: z.string(),
  revision: z.number(),
  status: z.string(),
  recordedAt: z.string(),
  etag: z.string(),
});

/** Correlated fetch wrapper: rejects invalid payloads and surfaces problem contracts. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: ProblemRuntime | null,
    readonly correlationId: string | null,
  ) {
    super(
      problem?.detail ?? problem?.title ?? `Request failed with status ${String(status)}`,
    );
  }
}

export async function apiFetch(url: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(url, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.headers as Record<string, string> | undefined),
    },
  });
  if (response.ok) {
    return response;
  }
  const correlation = response.headers.get('X-Correlation-Id');
  let problem: ProblemRuntime | null = null;
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('application/problem+json')) {
    const parsed = ProblemSchema.safeParse(await response.json().catch(() => null));
    if (parsed.success) {
      problem = parsed.data;
    }
  }
  throw new ApiError(response.status, problem, correlation);
}

/** Parses a JSON response with runtime validation; invalid payloads are rejected. */
export async function parseJson<T>(response: Response, schema: z.ZodType<T>): Promise<T> {
  const body: unknown = await response.json().catch(() => null);
  const parsed = schema.safeParse(body);
  if (!parsed.success) {
    throw new ApiError(response.status, null, response.headers.get('X-Correlation-Id'));
  }
  return parsed.data;
}
