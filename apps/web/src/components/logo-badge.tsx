import { cn } from "@/lib/cn";

// Placeholder brand mark — a simple circular monogram standing in for
// Farelo de Bolo's real logo file, which the frontend doesn't have an
// asset for yet. Swap the "FB" span for an <img>/<svg> once a real
// logo asset exists (see the design canvas shared with the user).
export function LogoBadge({
  size = 34,
  className,
}: {
  size?: number;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "bg-primary-soft text-primary-dark flex shrink-0 items-center justify-center rounded-full",
        className,
      )}
      style={{ width: size, height: size }}
    >
      <span
        className="font-serif font-semibold italic"
        style={{ fontSize: size * 0.38 }}
      >
        FB
      </span>
    </span>
  );
}
