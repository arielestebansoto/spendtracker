import { apiFetch } from "./api";

const CURRENT_PRIVACY_VERSION = "1.0";
const CURRENT_TERMS_VERSION = "1.0";

export interface ConsentStatus {
  hasAcceptedPolicies: boolean;
  privacyPolicyVersion: string | null;
  termsVersion: string | null;
  acceptedAt: string | null;
}

export async function checkConsentStatus(): Promise<ConsentStatus> {
  const response = await apiFetch("/api/v1/user/consent");

  if (response.status === 401) {
    return {
      hasAcceptedPolicies: false,
      privacyPolicyVersion: null,
      termsVersion: null,
      acceptedAt: null,
    };
  }

  return response.json();
}

export async function recordConsent(): Promise<ConsentStatus> {
  const response = await apiFetch("/api/v1/user/consent", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      privacyPolicyVersion: CURRENT_PRIVACY_VERSION,
      termsVersion: CURRENT_TERMS_VERSION,
    }),
  });

  if (!response.ok) {
    throw new Error("Failed to record consent");
  }

  return response.json();
}

export function getCurrentPrivacyVersion(): string {
  return CURRENT_PRIVACY_VERSION;
}

export function getCurrentTermsVersion(): string {
  return CURRENT_TERMS_VERSION;
}
