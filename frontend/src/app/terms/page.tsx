import Link from "next/link";
import Navbar from "../components/Navbar";

export const metadata = {
  title: "Terms of Service - SpendTracker",
  description: "Terms of Service for SpendTracker expense tracking application.",
};

export default function TermsPage() {
  return (
    <div className="min-h-screen bg-background">
      <Navbar />
      <main className="max-w-3xl mx-auto px-6 py-16">
        <h1 className="text-4xl font-bold mb-8">Terms of Service</h1>
        <p className="text-muted-foreground mb-8">Last updated: August 17, 2026</p>

        <div className="space-y-8 text-foreground">
          <section>
            <h2 className="text-2xl font-semibold mb-4">1. What Is SpendTracker</h2>
            <p>SpendTracker is a personal expense tracking application that allows you to record and manage your financial expenses. It is an academic portfolio project.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">2. User Responsibilities</h2>
            <ul className="list-disc pl-6 space-y-2">
              <li>You are responsible for the accuracy of expense data you enter.</li>
              <li>You are responsible for maintaining the security of your OAuth account.</li>
              <li>You agree to use the application only for its intended purpose: personal expense tracking.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">3. Prohibited Use</h2>
            <p className="mb-2">You may not:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Use the application for any illegal purpose.</li>
              <li>Attempt to access other users&apos; data.</li>
              <li>Attempt to compromise the security of the application.</li>
              <li>Use automated tools to interact with the application without authorization.</li>
              <li>Resell or redistribute the service.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">4. Account Suspension or Deletion</h2>
            <p>We reserve the right to suspend or delete accounts that violate these terms. You may also request account deletion at any time by contacting us.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">5. Intellectual Property</h2>
            <p>The application code, design, and branding are the property of the project maintainer. Your expense data remains yours at all times.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">6. User-Generated Content</h2>
            <p>All expense data and receipts you upload are owned by you. You grant us only the right to store and display this data to you through the application.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">7. Service Availability</h2>
            <p>This is an academic project. We do not guarantee uptime, availability, or data persistence. Use at your own risk. We recommend keeping your own backups of important financial data.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">8. Changes to the Service</h2>
            <p>We may modify, update, or discontinue the service at any time without prior notice.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">9. Changes to These Terms</h2>
            <p>We may update these terms at any time. Continued use of the application after changes constitutes acceptance of the new terms.</p>
          </section>

        </div>

        <div className="mt-12 pt-8 border-t">
          <Link href="/" className="text-primary hover:underline">
            ← Back to SpendTracker
          </Link>
        </div>
      </main>
    </div>
  );
}
