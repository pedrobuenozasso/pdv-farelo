"use client";

import { useSyncExternalStore } from "react";

import { getSession, type Session } from "@/lib/auth";

function subscribeToStorage(callback: () => void): () => void {
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

function getServerSnapshot(): null {
  return null;
}

// `useSyncExternalStore`, not `useState` + `useEffect`, to read this
// client-only (localStorage) value — the recommended way to read mutable
// state that lives outside React without the "setState inside an effect"
// anti-pattern the project's stricter React Compiler-era lint rules flag
// (react-hooks/set-state-in-effect): `getServerSnapshot` naturally
// returns `null` for both the server render and the first client
// hydration pass (avoiding a mismatch), then React re-renders with the
// real session once hydrated — no manual "mounted" state needed. Split
// out from auth.ts (a plain, hook-free module reachable from Server
// Components too) since a hook import there breaks that build — see that
// file's comment.
export function useSession(): Session | null {
  return useSyncExternalStore(
    subscribeToStorage,
    getSession,
    getServerSnapshot,
  );
}
