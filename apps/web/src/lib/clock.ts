import { useSyncExternalStore } from "react";

// Shared "current time" tick, read via `useSyncExternalStore` rather than
// `useState` + `useEffect` + `setInterval` — calling `Date.now()`/`new
// Date()` (no arguments) directly during render is flagged as an impure
// render by this project's stricter React Compiler-era lint rules
// (react-hooks/purity); `useSyncExternalStore` is the sanctioned escape
// hatch for reading a value that changes outside of React's control.
// Returns whole seconds (not ms) so the snapshot is stable between the
// once-a-second notifications — a snapshot that changes on every call
// (like raw `Date.now()`) would make React think the store is tearing
// and force extra re-renders.
function subscribe(callback: () => void): () => void {
  const id = setInterval(callback, 1_000);
  return () => clearInterval(id);
}

function getSnapshot(): number {
  return Math.floor(Date.now() / 1_000);
}

function getServerSnapshot(): number {
  return 0;
}

export function useNowSeconds(): number {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
