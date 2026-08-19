# Bank portal

Back-office web application for the Digital Lending Platform. Next.js 16 (App
Router), TypeScript, Tailwind CSS 4.

## Position in the platform

This is a presentation layer. It holds no lending logic: eligibility, limits,
pricing, EMI, workflow transitions and approval authority are all decided by the
Spring Boot API and rendered here.

The browser never calls the API directly. Server Components fetch server-side,
and client components call this application's own route handlers under `/bff/**`,
which reach the API inside the container network. That keeps the API off the
public origin and removes the need for CORS between portal and API.

## Structure

```
src/
├── app/
│   ├── bff/health/            portal liveness, used by the container healthcheck
│   ├── bff/platform/          proxy to the API for client-side polling
│   ├── system/health/         system health page
│   ├── layout.tsx             shell, navigation, query provider
│   └── page.tsx               overview
├── components/                presentational components
└── lib/
    ├── api/backend.ts         server-side API client: correlation id, timeout, validation
    ├── api/contracts.ts       zod schemas for the API contract
    ├── env.ts                 server and public configuration
    └── cn.ts                  class name helper
```

## Conventions

- **Validate at the boundary.** Every API response is parsed with a zod schema
  before it reaches a component, so a contract change fails visibly and locally.
- **Decimals are strings.** Money and rates arrive as decimal strings. Parse them
  with a decimal library, never `parseFloat`.
- **Failures are values.** `fetchFromBackend` returns a result rather than
  throwing, because a system page has to render a dependency being down.
- **Server-only stays server-only.** `lib/env.ts` and `lib/api/backend.ts` import
  `server-only`; the backend URL is never a `NEXT_PUBLIC_` value.

## Commands

```bash
npm run dev
```

```bash
npm run build
```

```bash
npm run lint
```

Copy `.env.example` to `.env.local` for local development so
`BACKEND_INTERNAL_URL` points at a running backend.

## Dependencies

Deliberately minimal for the foundation milestone: TanStack Query, zod, clsx and
tailwind-merge. shadcn/ui, React Hook Form, AG Grid and a charting library are
added by the milestones that first need them (35 onwards), not in advance.
