"use client";

import Link from "next/link";
import { useEffect } from "react";

type DrawerProps = {
  open: boolean;
  onClose: () => void;
  links: { href: string; label: string }[];
  currentPath: string;
  onLogout?: () => void;
};

export default function Drawer({
  open,
  onClose,
  links,
  currentPath,
  onLogout,
}: DrawerProps) {
  useEffect(() => {
    if (open) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  return (
    <>
      {open && (
        <div
          className="fixed inset-0 z-50 bg-black/40 md:hidden"
          onClick={onClose}
        />
      )}

      <div
        className={`fixed top-0 right-0 z-50 h-full w-72 bg-card shadow-xl md:hidden transition-transform duration-300 ${
          open ? "translate-x-0" : "translate-x-full"
        }`}
      >
        <div className="flex items-center justify-between px-4 h-14 border-b border-border">
          <span className="text-lg font-semibold">Menu</span>
          <button
            onClick={onClose}
            className="p-2 -mr-2 rounded-lg hover:bg-accent transition"
            aria-label="Close menu"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <nav className="p-4 flex flex-col gap-1">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              onClick={onClose}
              className={`px-3 py-2.5 rounded-lg text-sm transition ${
                currentPath === link.href || currentPath.startsWith(link.href + "/")
                  ? "bg-accent text-accent-foreground font-medium"
                  : "text-muted-foreground hover:text-foreground hover:bg-accent"
              }`}
            >
              {link.label}
            </Link>
          ))}

          {onLogout && (
            <>
              <div className="my-2 border-t border-border" />
              <button
                onClick={() => {
                  onClose();
                  onLogout();
                }}
                className="px-3 py-2.5 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-accent transition text-left"
              >
                Log out
              </button>
            </>
          )}
        </nav>
      </div>
    </>
  );
}
