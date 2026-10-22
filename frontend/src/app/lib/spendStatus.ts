import { useEffect, useRef } from "react";

export type SpendStatus = "PENDING" | "PROCESSING" | "PROCESSED" | "FAILED";

export const POLL_INTERVAL_MS = 3000;

// A Spend still moving through extraction/enrichment: its values are provisional.
export function isInProgress(status: SpendStatus) {
  return status === "PENDING" || status === "PROCESSING";
}

// Calls `poll` every `intervalMs` while `active` is true. The timer is cleared as soon as
// `active` becomes false and when the component unmounts.
export function usePollWhile(
  active: boolean,
  poll: () => void,
  intervalMs: number = POLL_INTERVAL_MS
) {
  const pollRef = useRef(poll);

  useEffect(() => {
    pollRef.current = poll;
  });

  useEffect(() => {
    if (!active) return;
    const timer = setInterval(() => pollRef.current(), intervalMs);
    return () => clearInterval(timer);
  }, [active, intervalMs]);
}
