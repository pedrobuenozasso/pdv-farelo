"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode } from "react";

import { LogoBadge } from "@/components/logo-badge";
import { clearSession } from "@/lib/auth";
import { cn } from "@/lib/cn";
import { useSession } from "@/lib/use-session";

const SIDE_ITEMS = [
  {
    href: "/pdv",
    label: "PDV",
    icon: (
      <svg
        width="17"
        height="17"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M3 9h18M9 21V9" />
      </svg>
    ),
  },
  {
    href: "/kds",
    label: "Cozinha",
    icon: (
      <svg
        width="17"
        height="17"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M8 3v4M16 3v4M4 11h16M5 7h14a1 1 0 0 1 1 1v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a1 1 0 0 1 1-1Z" />
      </svg>
    ),
  },
  {
    href: "/admin/products",
    label: "Admin",
    icon: (
      <svg
        width="17"
        height="17"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <rect x="3" y="3" width="7" height="7" rx="1.5" />
        <rect x="14" y="3" width="7" height="7" rx="1.5" />
        <rect x="3" y="14" width="7" height="7" rx="1.5" />
        <rect x="14" y="14" width="7" height="7" rx="1.5" />
      </svg>
    ),
  },
] as const;

export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const loggedIn = useSession() !== null;

  function handleLogout() {
    clearSession();
    router.push("/login");
  }

  return (
    <div className="flex min-h-screen">
      <div className="border-line bg-surface flex w-56 shrink-0 flex-col gap-6 border-r p-4">
        <Link href="/pdv" className="flex items-center gap-2.5 px-1.5">
          <LogoBadge size={34} />
          <span className="font-serif text-base font-semibold italic">
            Farelo OS
          </span>
        </Link>
        <div className="flex flex-col gap-1">
          {SIDE_ITEMS.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-2.5 rounded-lg px-3.5 py-2.5 text-sm font-semibold",
                  active
                    ? "bg-primary-soft text-primary-dark"
                    : "text-ink-soft hover:bg-bg-alt",
                )}
              >
                {item.icon}
                {item.label}
              </Link>
            );
          })}
        </div>
        {loggedIn ? (
          <button
            type="button"
            onClick={handleLogout}
            className="text-ink-faint hover:bg-bg-alt hover:text-ink-soft mt-auto flex items-center gap-2.5 rounded-lg px-3.5 py-2.5 text-left text-sm font-semibold"
          >
            Sair
          </button>
        ) : null}
      </div>
      <div className="flex-1 p-9">{children}</div>
    </div>
  );
}
