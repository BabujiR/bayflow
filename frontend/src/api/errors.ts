/** RFC 7807 Problem Details as returned by every Bayflow service. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: { field: string; message: string }[];
}

const TYPE_PREFIX = 'urn:bayflow:problem:';

/** A failed API call. `code` is the problem type without its prefix, e.g. "invalid-credentials". */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly title: string;
  /** Field name → message, from validation errors (400). */
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, problem: ProblemDetail = {}) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.type?.startsWith(TYPE_PREFIX) ? problem.type.slice(TYPE_PREFIX.length) : 'unknown';
    this.title = problem.title ?? 'Error';
    this.fieldErrors = Object.fromEntries((problem.errors ?? []).map((e) => [e.field, e.message]));
  }

  /** True for errors the user can fix (4xx); false for server or network problems. */
  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500;
  }
}

export async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {};
  try {
    const text = await response.text();
    problem = text ? (JSON.parse(text) as ProblemDetail) : {};
  } catch {
    // Not JSON (e.g. a proxy error page): keep the status only.
  }
  return new ApiError(response.status, problem);
}

/** A readable message for any error thrown by the API layer. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof TypeError) {
    return 'Cannot reach Bayflow. Check your connection and try again.';
  }
  return 'Something went wrong. Please try again.';
}
