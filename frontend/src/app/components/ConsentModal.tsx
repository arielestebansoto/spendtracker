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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm">
      <div className="max-w-lg w-full mx-4 bg-card border rounded-lg shadow-lg p-8">
        <h2 className="text-2xl font-bold mb-4">Welcome to SpendTracker</h2>

        <p className="text-muted-foreground mb-6">
          Before you continue, please review and accept our policies:
        </p>

        <div className="space-y-4 mb-6">
          <Link
            href="/privacy"
            target="_blank"
            className="block p-4 border rounded-lg hover:bg-accent transition"
          >
            <h3 className="font-semibold">Privacy Policy</h3>
            <p className="text-sm text-muted-foreground mt-1">
              Learn what data we collect and how we protect it.
            </p>
          </Link>

          <Link
            href="/terms"
            target="_blank"
            className="block p-4 border rounded-lg hover:bg-accent transition"
          >
            <h3 className="font-semibold">Terms of Service</h3>
            <p className="text-sm text-muted-foreground mt-1">
              Review the rules for using SpendTracker.
            </p>
          </Link>
        </div>

        <label className="flex items-start gap-3 cursor-pointer mb-6">
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
            className="mt-1 h-4 w-4 rounded border-gray-300"
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
          <p className="text-sm text-red-500 mb-4">{error}</p>
        )}

        <button
          onClick={handleAccept}
          disabled={!agreed || loading}
          className="w-full py-3 rounded-lg bg-primary text-primary-foreground font-medium hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition"
        >
          {loading ? "Saving..." : "Continue"}
        </button>
      </div>
    </div>
  );
}
