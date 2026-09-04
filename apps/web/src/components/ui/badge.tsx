import { type ReactNode } from "react";

import { cn } from "@/lib/cn";

type BadgeTone = "primary" | "green" | "amber" | "red" | "neutral";

const TONE_CLASSES: Record<BadgeTone, string> = {
  primary: "bg-primary-soft text-primary-dark",
  green: "bg-green-soft text-green-ink",
  amber: "bg-amber-soft text-amber-ink",
  red: "bg-red-soft text-red-ink",
  neutral: "bg-bg-alt text-ink-soft",
};

export function Badge({
  tone = "neutral",
  dot = false,
  children,
}: {
  tone?: BadgeTone;
  dot?: boolean;
  children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold",
        TONE_CLASSES[tone],
      )}
    >
      {dot ? <span className="h-1.5 w-1.5 rounded-full bg-current" /> : null}
      {children}
    </span>
  );
}
