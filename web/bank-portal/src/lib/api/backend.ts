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
  /** Present for endpoints that require a signed-in caller. */
  bearerToken?: string,
): Promise<BackendResult<z.infer<T>>> {
  const correlationId = `portal-${randomUUID()}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), serverEnv.requestTimeoutMs);

  try {
    const response = await fetch(`${serverEnv.backendBaseUrl}${path}`, {
      headers: {
        Accept: "application/json",
        "X-Correlation-Id": correlationId,
        ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
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

/**
 * Server-side POST to the Spring Boot API.
 *
 * <p>Shares the timeout, correlation id and envelope validation of the GET
 * helper. It differs in one way that matters: a rejected sign-in is a 401 with
 * a meaningful message, so the backend's own message and status are carried
 * back rather than flattened into "not reachable". The portal must be able to
 * tell "wrong password" from "the API is down".
 */
export async function postToBackend<T extends z.ZodTypeAny>(
  path: string,
  body: unknown,
  payloadSchema: T,
  bearerToken?: string,
): Promise<BackendResult<z.infer<T>>> {
  const correlationId = `portal-${randomUUID()}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), serverEnv.requestTimeoutMs);

  try {
    const response = await fetch(`${serverEnv.backendBaseUrl}${path}`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "X-Correlation-Id": correlationId,
        ...(bearerToken ? { Authorization: `Bearer ${bearerToken}` } : {}),
      },
      body: JSON.stringify(body ?? {}),
      signal: controller.signal,
      cache: "no-store",
    });

    const raw: unknown = await response.json().catch(() => null);
    const parsed = envelopeOf(payloadSchema).safeParse(raw);

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
        reason: envelope.error?.message ?? "The request was refused.",
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
