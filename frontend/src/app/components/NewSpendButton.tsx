"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "./AuthProvider";

// The spends list and a Spend's detail page, but not /spends/new itself.
function isShownOn(pathname: string) {
  return pathname === "/spends" || /^\/spends\/(?!new$)[^/]+$/.test(pathname);
}

// A round "+" centered at the bottom that goes to the new spend page. The new spend page puts
// its camera button in this same spot (spends/new/page.tsx).
export default function NewSpendButton() {
  const { user } = useAuth();
  const pathname = usePathname();

  if (!user || !isShownOn(pathname)) return null;

  return (
    <>
      {/* Room at the end of the page, so the button never covers the last row. */}
      <div className="h-24" aria-hidden />

      <Link href="/spends/new" aria-label="New spend" className={floatingButtonClass}>
        <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
        </svg>
      </Link>
    </>
  );
}

// Shared by the "+" and the camera button, so both sit in exactly the same spot.
export const floatingButtonClass =
  "fixed bottom-6 left-1/2 -translate-x-1/2 z-40 flex items-center justify-center w-14 h-14 rounded-full bg-primary text-primary-foreground shadow-lg hover:opacity-90 transition";
