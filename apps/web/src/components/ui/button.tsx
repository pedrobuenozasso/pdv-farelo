import { type ButtonHTMLAttributes } from "react";

import { cn } from "@/lib/cn";

type ButtonVariant = "primary" | "dark" | "outline" | "danger" | "ghost-danger";

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: "bg-primary text-primary-ink hover:bg-primary-dark",
  dark: "bg-ink text-surface hover:opacity-90",
  outline: "border border-line bg-transparent text-ink hover:bg-bg-alt",
  danger: "bg-red text-white hover:opacity-90",
  "ghost-danger":
    "border border-red/30 bg-transparent text-red hover:bg-red-soft",
};

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({
  variant = "primary",
  className,
  ...props
}: ButtonProps) {
  return (
    <button
      className={cn(
        "rounded-full px-5 py-2.5 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40",
        VARIANT_CLASSES[variant],
        className,
      )}
      {...props}
    />
  );
}
