"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

import { LogoBadge } from "@/components/logo-badge";
import { clearSession } from "@/lib/auth";
import { cn } from "@/lib/cn";
import { useSession } from "@/lib/use-session";

const TABS = [
  { href: "/pdv", label: "PDV" },
  { href: "/kds", label: "Cozinha" },
  { href: "/admin/products", label: "Admin" },
] as const;

// Shared top bar for the internal tools (/pdv, /admin/*) — matches the
// design canvas. Deliberately NOT used on /kds: the kitchen display is a
// distraction-free, full-screen, high-contrast surface (see that page),
// and NOT used on the public customer menu (/c/[commandNumber]), which
// has its own client-facing header.
export function InternalNav() {
  const pathname = usePathname();
  const router = useRouter();
  const loggedIn = useSession() !== null;

  function handleLogout() {
    clearSession();
    router.push("/login");
  }

  return (
    <div className="border-line bg-surface flex items-center justify-between border-b px-8 py-3.5">
      <Link href="/pdv" className="flex items-center gap-2.5">
        <LogoBadge size={34} />
        <span className="font-serif text-base font-semibold italic">
          Farelo OS
        </span>
      </Link>
      <div className="flex items-center gap-1">
        {TABS.map((tab) => {
          const active = pathname.startsWith(tab.href);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              className={cn(
                "rounded-lg px-3.5 py-2 text-sm font-semibold",
                active
                  ? "bg-primary-soft text-primary-dark"
                  : "text-ink-soft hover:bg-bg-alt",
              )}
            >
              {tab.label}
            </Link>
          );
        })}
        {loggedIn ? (
          <button
            type="button"
            onClick={handleLogout}
            className="text-ink-faint hover:bg-bg-alt hover:text-ink-soft ml-2 rounded-lg px-3.5 py-2 text-sm font-semibold"
          >
            Sair
          </button>
        ) : null}
      </div>
    </div>
  );
}
