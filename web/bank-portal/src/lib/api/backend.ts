import "server-only";

import { randomUUID } from "node:crypto";
import type { z } from "zod";

import { envelopeOf } from "@/lib/api/contracts";
import { serverEnv } from "@/lib/env";

/**
 * Server-side access to the Spring Boot API.
 *
 * Every call carries a correlation id, is bounded by a timeout, and has its
 * envelope validated before the payload is handed on. Failures are returned as
 * values rather than thrown, because a system page has to render a dependency
 * being down rather than collapse with it.
 */

export type BackendSuccess<T> = {
  ok: true;
  data: T;
  correlationId: string;
};

export type BackendFailure = {
  ok: false;
  /** Operator-facing explanation. Never contains backend internals. */
  reason: string;
  correlationId: string;
  status?: number;
};

export type BackendResult<T> = BackendSuccess<T> | BackendFailure;

export async function fetchFromBackend<T extends z.ZodTypeAny>(
  path: string,
  payloadSchema: T,
): Promise<BackendResult<z.infer<T>>> {
  const correlationId = `portal-${randomUUID()}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), serverEnv.requestTimeoutMs);

  try {
    const response = await fetch(`${serverEnv.backendBaseUrl}${path}`, {
      headers: {
        Accept: "application/json",
        "X-Correlation-Id": correlationId,
      },
      signal: controller.signal,
      // Connectivity state is never cached: a stale "everything is fine" is worse
      // than a slow answer.
      cache: "no-store",
    });

    const body: unknown = await response.json().catch(() => null);
    const parsed = envelopeOf(payloadSchema).safeParse(body);

    if (!parsed.success) {
      return {
        ok: false,
        reason: `The API answered with ${response.status} in a shape this portal does not recognise.`,
        correlationId,
        status: response.status,
      };
    }

    const envelope = parsed.data;
    const returnedCorrelationId = envelope.correlationId ?? correlationId;

    if (!envelope.success || envelope.data === undefined) {
      return {
        ok: false,
        reason: envelope.error?.message ?? "The API reported a failure without detail.",
        correlationId: returnedCorrelationId,
        status: response.status,
      };
    }

    return { ok: true, data: envelope.data, correlationId: returnedCorrelationId };
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return {
      ok: false,
      reason: timedOut
        ? `The API did not answer within ${serverEnv.requestTimeoutMs} ms.`
        : "The API is not reachable from the portal.",
      correlationId,
    };
  } finally {
    clearTimeout(timeout);
  }
}
