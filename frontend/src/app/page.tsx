"use client";

import Link from "next/link";
import Navbar from "./components/Navbar";
import { apiUrl } from "./lib/api";

export default function HomePage() {
  const loginGoogle = () => {
    window.location.href = apiUrl("/oauth2/authorization/google");
  };

  const loginGithub = () => {
    window.location.href = apiUrl("/oauth2/authorization/github");
  };

  return (
    <div className="min-h-screen bg-background">
      <Navbar />

      {/* Hero */}
      <main className="flex items-center justify-center px-6">
        <div className="max-w-2xl text-center py-24">
          <h1 className="text-5xl md:text-6xl font-bold tracking-tight">
            Spend Tracker AI
          </h1>

          <p className="mt-6 text-lg text-muted-foreground">
            Understand where your money goes.
          </p>

          <div className="mt-10 flex flex-col sm:flex-row gap-4 justify-center">
            <button
              onClick={loginGoogle}
              className="
                px-6 py-3
                rounded-lg
                border
                text-white
                hover:bg-gray-100
                hover:text-black
                transition
              "
            >
              Continue with Google
            </button>

            <button
              onClick={loginGithub}
              className="
                px-6 py-3
                rounded-lg
                border
                text-white
                hover:bg-gray-100
                hover:text-black
                transition
              "
            >
              Continue with GitHub
            </button>
          </div>
        </div>
      </main>

      <footer className="absolute bottom-0 w-full py-6">
        <div className="flex justify-center gap-6 text-sm text-muted-foreground">
          <Link href="/privacy" className="hover:underline">
            Privacy Policy
          </Link>
          <Link href="/terms" className="hover:underline">
            Terms of Service
          </Link>
        </div>
      </footer>
    </div>
  );
}