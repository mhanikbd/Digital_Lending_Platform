/**
 * Liveness of the portal process itself.
 *
 * Deliberately does not touch the backend: the container orchestrator must
 * restart this container when the portal is broken, not when a dependency
 * elsewhere in the platform is having a bad day.
 */
export async function GET() {
  return Response.json({ status: "UP", service: "bank-portal" });
}
