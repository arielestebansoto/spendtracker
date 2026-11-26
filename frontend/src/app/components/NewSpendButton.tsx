import Link from "next/link";

// Shared by the "+" and the camera button on /spends/new, so both sit in exactly the same spot.
export const floatingButtonClass =
  "fixed bottom-6 left-1/2 -translate-x-1/2 z-40 flex items-center justify-center w-14 h-14 rounded-full bg-primary text-primary-foreground shadow-lg hover:opacity-90 transition";

// A round "+" centered at the bottom that goes to the new spend page. Rendered by each page that
// shows it (the spends list and the detail page), not by the root layout: from the layout it did
// not stay fixed on iPhone Safari, while the same button inside the page did.
// Pages that render it leave room at the end (pb-24), so it never covers the last row.
export default function NewSpendButton() {
  return (
    <Link href="/spends/new" aria-label="New spend" className={floatingButtonClass}>
      <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
      </svg>
    </Link>
  );
}
