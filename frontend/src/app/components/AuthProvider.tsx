"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { initializeSession } from "@/app/lib/auth";
import { checkConsentStatus } from "@/app/lib/consent";
import ConsentModal from "./ConsentModal";

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

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const currentUser = await initializeSession();

        if (cancelled) return;

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

        const consent = await checkConsentStatus();
        if (!cancelled && !consent.hasAcceptedPolicies) {
          setNeedsConsent(true);
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [pathname, router]);

  const handleConsentGiven = useCallback(() => {
    setNeedsConsent(false);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isLoading }}>
      {children}
      {needsConsent && <ConsentModal onConsentGiven={handleConsentGiven} />}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
