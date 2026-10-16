// Parse an operator-entered BRL amount without floating-point arithmetic.
// Comma and dot are accepted as decimal separators; grouping and exponent
// notation are not valid on a payment form.
export function parseMoneyCents(value: string): number | null {
  const match = /^(\d+)(?:[.,](\d{1,2}))?$/.exec(value.trim());
  if (!match) return null;

  const cents =
    Number(match[1]) * 100 + Number((match[2] ?? "").padEnd(2, "0"));
  return Number.isSafeInteger(cents) ? cents : null;
}
