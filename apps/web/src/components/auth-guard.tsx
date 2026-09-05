"use client";

import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect } from "react";

import { getSession } from "@/lib/auth";
import type { UserRole } from "@/lib/api/users";
import { useSession } from "@/lib/use-session";

// Gates the internal tools (/pdv, /admin/*, /kds) behind a logged-in
// session. Checked client-side only — the backend's own @RequireRole
// checks (FARELO-122/123/124) remain the real enforcement; this is
// purely a UX redirect so staff land on /login instead of a
// silently-broken page (see the "Carregando..."/401 gap this closes).
// Deliberately not a Next.js middleware: the JWT lives in localStorage,
// not a cookie, so middleware (which only sees the request, not
// localStorage) can't read it anyway — same reasoning as `auth.ts`'s
// "no server round-trip to check validity" choice.
//
// Two different reads of the session, deliberately: the redirect
// decision below calls the plain `getSession()` directly inside the
// effect (which only ever runs on the client, after mount — never during
// SSR) rather than trusting the `useSession()` hook's render-time value,
// which is `null` on the server render AND on the first client hydration
// pass by design (see use-session.ts) — redirecting off that transient
// `null` would bounce an already-logged-in staff member to /login for a
// frame. `useSession()` still governs what's rendered below (worst case:
// a legitimately logged-in user briefly renders nothing while React
// corrects the hydration mismatch, never a leak of protected content).
// FARELO-304 ("Guardas de rota... conforme role"): `allow`, when given,
// restricts a route to the listed roles instead of just "logged in". This
// is a separate branch from the redirect-to-/login effect above: a user
// who IS logged in but whose role isn't in `allow` shouldn't be bounced to
// /login (they have a valid session — that's not the problem) or silently
// shown nothing (indistinguishable from a loading/hydration flash). Instead
// they see an in-place "access denied" message, same "client-side check is
// UX only" reasoning as the rest of this file — the backend's @RequireRole
// on the actual write/read endpoints remains the real enforcement.
export function AuthGuard({
  children,
  allow,
}: {
  children: ReactNode;
  allow?: UserRole[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const session = useSession();

  useEffect(() => {
    if (getSession() === null) {
      router.replace(`/login?redirect=${encodeURIComponent(pathname)}`);
    }
  }, [pathname, router]);

  if (session === null) return null;

  if (allow && (session.role === null || !allow.includes(session.role))) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center p-8">
        <p className="text-center text-sm text-neutral-500">
          Você não tem permissão para acessar esta página.
        </p>
      </div>
    );
  }

  return <>{children}</>;
}
