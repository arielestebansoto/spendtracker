"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { getCurrentUser } from "@/app/lib/auth";
import { checkConsentStatus } from "@/app/lib/consent";
import ConsentModal from "./ConsentModal";
import ErrorState from "./ErrorState";

type User = {
  id: string;
  name: string;
  email: string;
};

type AuthContextType = {
  user: User | null;
  isLoading: boolean;
};

const AuthContext = createContext<AuthContextType>({
  user: null,
  isLoading: true,
});

const PUBLIC_ROUTES = ["/", "/privacy", "/terms"];

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [needsConsent, setNeedsConsent] = useState(false);
  // Set when the session check fails without a response (backend down, no connection).
  // A 401 is not an error: getCurrentUser() returns null and the user goes to the login page.
  const [error, setError] = useState(false);
  // "Try again" bumps this counter to check the session again.
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const currentUser = await getCurrentUser();

        if (cancelled) return;
        setError(false);

        if (!currentUser) {
          setUser(null);
          setNeedsConsent(false);
          if (!PUBLIC_ROUTES.includes(pathname)) {
            router.replace("/");
          }
          return;
        }

        setUser(currentUser);

        if (pathname === "/") {
          router.replace("/dashboard");
          return;
        }

        if (!PUBLIC_ROUTES.includes(pathname)) {
          const consent = await checkConsentStatus();
          if (!cancelled && !consent.hasAcceptedPolicies) {
            setNeedsConsent(true);
          }
        }
      } catch {
        if (!cancelled) setError(true);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [pathname, router, retryCount]);

  const handleConsentGiven = useCallback(() => {
    setNeedsConsent(false);
  }, []);

  function retry() {
    setError(false);
    setIsLoading(true);
    setRetryCount((count) => count + 1);
  }

  // Public routes work without a session, so they render as usual.
  const showError = error && !PUBLIC_ROUTES.includes(pathname);

  return (
    <AuthContext.Provider value={{ user, isLoading }}>
      {showError ? (
        <ErrorState message="Could not reach the server." onRetry={retry} />
      ) : (
        children
      )}
      {needsConsent && <ConsentModal onConsentGiven={handleConsentGiven} />}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
