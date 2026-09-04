import { type HTMLAttributes } from "react";

import { cn } from "@/lib/cn";

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "border-line bg-surface rounded-2xl border p-5 shadow-[0_1px_2px_oklch(30%_0.03_50_/_6%),0_8px_20px_oklch(30%_0.03_50_/_7%)]",
        className,
      )}
      {...props}
    />
  );
}
