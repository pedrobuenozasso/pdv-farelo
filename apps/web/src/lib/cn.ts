// Minimal className joiner — filters falsy values. Not a substitute for
// `clsx`/`tailwind-merge` (no conflict resolution between competing
// utility classes), but this codebase composes classes additively, never
// conflictingly, so the extra dependency would be YAGNI.
export function cn(
  ...classes: Array<string | false | null | undefined>
): string {
  return classes.filter(Boolean).join(" ");
}
