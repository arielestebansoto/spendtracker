import { useEffect, useRef } from "react";

export type SpendStatus = "PENDING" | "PROCESSING" | "PROCESSED" | "FAILED";

export const POLL_INTERVAL_MS = 3000;

export const MAX_POLL_INTERVAL_MS = 30000;

// A Spend still moving through extraction/enrichment: its values are provisional.
export function isInProgress(status: SpendStatus) {
  return status === "PENDING" || status === "PROCESSING";
}

// Calls `poll` while `active` is true, first after `intervalMs` and then doubling the delay
// up to MAX_POLL_INTERVAL_MS. The delay starts over each time `active` becomes true.
// No polls are made while the tab is hidden; when it becomes visible again it polls at once
// and starts over from `intervalMs`. Timers and listeners are removed as soon as `active`
// becomes false and when the component unmounts.
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

    let delay = intervalMs;
    let timer: ReturnType<typeof setTimeout> | undefined;

    function scheduleNextPoll() {
      timer = setTimeout(() => {
        pollRef.current();
        delay = Math.min(delay * 2, MAX_POLL_INTERVAL_MS);
        scheduleNextPoll();
      }, delay);
    }

    function handleVisibilityChange() {
      clearTimeout(timer);
      if (document.hidden) return;

      pollRef.current();
      delay = intervalMs;
      scheduleNextPoll();
    }

    if (!document.hidden) scheduleNextPoll();
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      clearTimeout(timer);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [active, intervalMs]);
}
