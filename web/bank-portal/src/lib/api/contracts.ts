import { z } from "zod";

/**
 * Runtime shapes of the backend contract.
 *
 * The portal validates every response at the boundary instead of trusting a
 * TypeScript type that only exists at compile time. A backend that changes shape
 * then produces a clear, localised failure rather than an undefined deep inside
 * a component. These schemas are hand-written for Milestone 1; once more
 * endpoints exist they are generated from the OpenAPI document the backend
 * publishes at /v3/api-docs.
 */

export const apiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  violations: z
    .array(z.object({ field: z.string(), message: z.string() }))
    .optional(),
});

/** Wraps a payload schema in the standard ApiResponse envelope. */
export function envelopeOf<T extends z.ZodTypeAny>(payload: T) {
  return z.object({
    success: z.boolean(),
    // Absent rather than null: the backend omits null properties.
    data: payload.optional(),
    error: apiErrorSchema.optional(),
    correlationId: z.string().optional(),
    timestamp: z.string().optional(),
  });
}

export const componentStatusSchema = z.object({
  name: z.string(),
  status: z.enum(["UP", "DOWN"]),
  detail: z.string(),
});

export const platformHealthSchema = z.object({
  status: z.enum(["UP", "DOWN"]),
  components: z.array(componentStatusSchema),
});

export const platformInfoSchema = z.object({
  application: z.string(),
  apiVersion: z.string(),
  environment: z.string(),
  serverTime: z.string(),
});

export type ApiError = z.infer<typeof apiErrorSchema>;
export type ComponentStatus = z.infer<typeof componentStatusSchema>;
export type PlatformHealth = z.infer<typeof platformHealthSchema>;
export type PlatformInfo = z.infer<typeof platformInfoSchema>;
