# Mobile applications

Two Flutter applications are planned here:

| Directory            | Application                | Milestone |
| -------------------- | -------------------------- | --------- |
| `customer-app/`      | Customer mobile app        | 33        |
| `field-officer-app/` | Field Officer mobile app   | 34        |

**Status: not started.** Flutter work was deliberately deferred so that the
backend contract is settled first. Nothing in the platform depends on these
directories existing.

## What they will build against

Both apps are clients of the same authoritative API as the bank portal. They own
no lending logic of their own: eligibility, limits, pricing, EMI, workflow
transitions and approval authority are all decided by the backend.

- API base path: `/api/v1`
- Response envelope: `success`, `data`, `error`, `correlationId`, `timestamp`
- Money and rates arrive as **decimal strings**, not JSON numbers. Parse them
  with a decimal type, never `double.parse`.
- Every request should send `X-Correlation-Id`; the value comes back on the
  response and in the server logs.
- The contract is published at `/v3/api-docs`, so models can be generated rather
  than hand-written.

Planned stack, from the implementation specification: Flutter, Riverpod, Dio,
GoRouter, Freezed, JSON serialization, Flutter Secure Storage.
