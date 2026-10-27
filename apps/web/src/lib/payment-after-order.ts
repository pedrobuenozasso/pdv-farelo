// The order has already been committed when this runs. A balance lookup
// failure must not turn that successful order into a failed mutation, and
// the payment form must never inherit the pre-order balance.
export async function freshPaymentAmount(
  loadBalance: () => Promise<{ remaining: number }>,
): Promise<number | null> {
  try {
    const balance = await loadBalance();
    return balance.remaining;
  } catch {
    return null;
  }
}
