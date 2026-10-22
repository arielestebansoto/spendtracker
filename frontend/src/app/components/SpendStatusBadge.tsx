import { SpendStatus, isInProgress } from "@/app/lib/spendStatus";

// Shows the processing state of a Spend. A PROCESSED Spend shows nothing: its extracted data is
// the "done" state.
export default function SpendStatusBadge({ status }: { status: SpendStatus }) {
  if (isInProgress(status)) {
    return (
      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-muted text-xs font-medium text-muted-foreground">
        <span className="animate-spin w-3 h-3 border-2 border-primary border-t-transparent rounded-full" />
        Processing
      </span>
    );
  }

  if (status === "FAILED") {
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-destructive/10 text-xs font-medium text-destructive">
        Failed
      </span>
    );
  }

  return null;
}
