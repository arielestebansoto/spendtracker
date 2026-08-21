"use client";

import Link from "next/link";
import { useState } from "react";
import { recordConsent } from "@/app/lib/consent";

interface ConsentModalProps {
  onConsentGiven: () => void;
}

export default function ConsentModal({ onConsentGiven }: ConsentModalProps) {
  const [agreed, setAgreed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAccept = async () => {
    setLoading(true);
    setError(null);

    try {
      await recordConsent();
      onConsentGiven();
    } catch {
      setError("Failed to save your consent. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-card rounded-xl shadow-xl w-full max-w-md p-6">
        <h2 className="text-lg font-semibold">Welcome to Spendtracker</h2>

        <p className="mt-2 text-sm text-muted-foreground">
          Before you continue, please review and accept our policies:
        </p>

        <div className="mt-4 space-y-2">
          <Link
            href="/privacy"
            target="_blank"
            className="block p-3 rounded-lg border border-border hover:bg-accent transition"
          >
            <h3 className="text-sm font-medium">Privacy Policy</h3>
            <p className="text-xs text-muted-foreground mt-0.5">
              Learn what data we collect and how we protect it.
            </p>
          </Link>

          <Link
            href="/terms"
            target="_blank"
            className="block p-3 rounded-lg border border-border hover:bg-accent transition"
          >
            <h3 className="text-sm font-medium">Terms of Service</h3>
            <p className="text-xs text-muted-foreground mt-0.5">
              Review the rules for using Spendtracker.
            </p>
          </Link>
        </div>

        <label className="flex items-start gap-3 mt-4 cursor-pointer">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
            className="mt-0.5 h-4 w-4 rounded border-border"
          />
          <span className="text-sm">
            I have read and agree to the{" "}
            <Link href="/privacy" target="_blank" className="underline">
              Privacy Policy
            </Link>{" "}
            and{" "}
            <Link href="/terms" target="_blank" className="underline">
              Terms of Service
            </Link>
          </span>
        </label>

        {error && (
          <p className="mt-3 text-sm text-destructive">{error}</p>
        )}

        <button
          onClick={handleAccept}
          disabled={!agreed || loading}
          className="mt-4 w-full py-2.5 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition"
        >
          {loading ? "Saving..." : "Continue"}
        </button>
      </div>
    </div>
  );
}
