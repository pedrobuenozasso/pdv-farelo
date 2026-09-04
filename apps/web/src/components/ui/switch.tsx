import { type InputHTMLAttributes, forwardRef } from "react";

import { cn } from "@/lib/cn";

type SwitchProps = Omit<InputHTMLAttributes<HTMLInputElement>, "type">;

// A checkbox visually restyled as a pill toggle (matches the design
// canvas) — still a real <input type="checkbox">, so react-hook-form's
// register() works unmodified and it stays keyboard/screen-reader
// accessible for free.
export const Switch = forwardRef<HTMLInputElement, SwitchProps>(function Switch(
  { className, ...props },
  ref,
) {
  return (
    <label
      className={cn("group relative inline-flex cursor-pointer", className)}
    >
      <input ref={ref} type="checkbox" className="peer sr-only" {...props} />
      <span className="bg-line peer-checked:bg-primary h-[19px] w-[34px] rounded-full transition-colors" />
      <span className="bg-surface absolute top-[2px] left-[2px] h-[15px] w-[15px] rounded-full transition-transform peer-checked:translate-x-[15px]" />
    </label>
  );
});
