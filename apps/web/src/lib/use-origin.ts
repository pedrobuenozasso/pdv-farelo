"use client";

import { useSyncExternalStore } from "react";

// FARELO-250: each comanda's public QR-code URL is {origin}/c/{number} —
// reusing app/c/[commandNumber] as-is, no separate short-link route. This
// hook reads window.location.origin the same "useSyncExternalStore, not
// useState + useEffect" way lib/clock.ts/use-session.ts already do for
// client-only reads (this project's stricter React-Compiler-era lint
// rules flag a plain-render `window` read as impure). No subscription is
// actually needed — the origin never changes during a page's lifetime —
// so `subscribe` is a no-op that never calls back.
function subscribe(): () => void {
  return () => {};
}

function getSnapshot(): string {
  return window.location.origin;
}

function getServerSnapshot(): string {
  return "";
}

export function useOrigin(): string {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
