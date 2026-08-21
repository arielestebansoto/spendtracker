"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "./AuthProvider";
import { logout } from "@/app/lib/auth";
import Drawer from "./Drawer";

export default function Navbar() {
  const { user } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [drawerOpen, setDrawerOpen] = useState(false);

  async function handleLogout() {
    await logout();
    router.replace("/");
  }

  const navLinks = [
    { href: "/dashboard", label: "Dashboard" },
    { href: "/spends", label: "Spends" },
    { href: "/settings", label: "Settings" },
  ];

  return (
    <>
      <header className="border-b border-border bg-card sticky top-0 z-40">
        <div className="mx-auto px-4 h-14 flex items-center justify-between max-w-5xl">
          <Link href="/" className="text-lg font-semibold">
            Spendtracker
          </Link>

          {user ? (
            <>
              <nav className="hidden md:flex items-center gap-1">
                {navLinks.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    className={`px-3 py-1.5 rounded-lg text-sm transition ${
                      pathname === link.href || pathname.startsWith(link.href + "/")
                        ? "bg-accent text-accent-foreground font-medium"
                        : "text-muted-foreground hover:text-foreground hover:bg-accent"
                    }`}
                  >
                    {link.label}
                  </Link>
                ))}
                <button
                  onClick={handleLogout}
                  className="ml-2 px-3 py-1.5 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-accent transition"
                >
                  Log out
                </button>
              </nav>

              <button
                onClick={() => setDrawerOpen(true)}
                className="md:hidden p-2 -mr-2 rounded-lg hover:bg-accent transition"
                aria-label="Open menu"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              </button>
            </>
          ) : (
            <div className="h-9" />
          )}
        </div>
      </header>

      <Drawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        links={user ? navLinks : []}
        currentPath={pathname}
        onLogout={user ? handleLogout : undefined}
      />
    </>
  );
}
