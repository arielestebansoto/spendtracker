"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/app/components/AuthProvider";
import { logout, deleteAccount } from "@/app/lib/auth";
import LoadingState from "@/app/components/LoadingState";
import DeleteAccountModal from "@/app/components/DeleteAccountModal";

export default function SettingsPage() {
  const { user, isLoading: authLoading } = useAuth();
  const router = useRouter();
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  async function handleLogout() {
    try {
      await logout();
    } catch {
      // session may already be invalid — proceed anyway
    }
    router.replace("/");
  }

  async function handleDeleteAccount() {
    await deleteAccount();
    try {
      await logout();
    } catch {
      // session may already be invalid — proceed anyway
    }
    router.replace("/");
  }

  if (authLoading || !user) return <LoadingState />;

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-8">Settings</h1>

      <div className="space-y-6">
        <section className="rounded-xl border border-border p-6">
          <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-4">
            Account
          </h2>
          <div className="space-y-3">
            <div>
              <p className="text-sm text-muted-foreground">Name</p>
              <p className="text-sm font-medium">{user.name}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">Email</p>
              <p className="text-sm font-medium">{user.email}</p>
            </div>
          </div>
        </section>

        <section className="rounded-xl border border-border p-6">
          <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-4">
            Legal
          </h2>
          <div className="flex flex-col gap-2">
            <Link
              href="/privacy"
              className="text-sm text-muted-foreground hover:text-foreground transition"
            >
              Privacy Policy →
            </Link>
            <Link
              href="/terms"
              className="text-sm text-muted-foreground hover:text-foreground transition"
            >
              Terms of Service →
            </Link>
          </div>
        </section>

        <section className="rounded-xl border border-border p-6">
          <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-4">
            Session
          </h2>
          <button
            onClick={handleLogout}
            className="px-4 py-2.5 rounded-lg border border-border text-sm font-medium hover:bg-accent transition"
          >
            Log out
          </button>
        </section>

        <section className="rounded-xl border border-destructive/30 p-6">
          <h2 className="text-sm font-semibold text-destructive uppercase tracking-wide mb-2">
            Danger zone
          </h2>
          <p className="text-sm text-muted-foreground mb-4">
            Permanently delete your account and all associated data. This cannot be undone.
          </p>
          <button
            onClick={() => setShowDeleteModal(true)}
            className="px-4 py-2.5 rounded-lg bg-destructive text-destructive-foreground text-sm font-medium hover:opacity-90 transition"
          >
            Delete account
          </button>
        </section>
      </div>

      {showDeleteModal && (
        <DeleteAccountModal
          onConfirm={handleDeleteAccount}
          onCancel={() => setShowDeleteModal(false)}
        />
      )}
    </div>
  );
}
