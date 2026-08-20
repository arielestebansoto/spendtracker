import Link from "next/link";
import Navbar from "../components/Navbar";

export const metadata = {
  title: "Privacy Policy - SpendTracker",
  description: "Privacy Policy for SpendTracker expense tracking application.",
};

export default function PrivacyPage() {
  return (
    <div className="min-h-screen bg-background">
      <Navbar />
      <main className="max-w-3xl mx-auto px-6 py-16">
        <h1 className="text-4xl font-bold mb-8">Privacy Policy</h1>
        <p className="text-muted-foreground mb-8">Last updated: August 17, 2026</p>

        <div className="space-y-8 text-foreground">
          <section>
            <h2 className="text-2xl font-semibold mb-4">1. What We Collect</h2>
            <p className="mb-2">SpendTracker collects only the information necessary to provide the service:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li><strong>Identity data:</strong> Name and email address, provided through your OAuth provider (Google or GitHub).</li>
              <li><strong>Expense data:</strong> Financial records you manually enter, including descriptions, amounts, categories, and dates.</li>
              <li><strong>Receipt files:</strong> Optional receipt images you choose to upload.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">2. Why We Collect It</h2>
            <ul className="list-disc pl-6 space-y-2">
              <li><strong>Identity data:</strong> To authenticate your account and provide access to your personal data.</li>
              <li><strong>Expense data:</strong> To provide expense tracking and display your financial information.</li>
              <li><strong>Receipt files:</strong> To associate receipts with your expenses for your own reference.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">3. OAuth Providers</h2>
            <p className="mb-2">We use OAuth2 authentication through:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li><strong>Google</strong> - For users who sign in with their Google account.</li>
              <li><strong>GitHub</strong> - For users who sign in with their GitHub account.</li>
            </ul>
            <p className="mt-2">We only receive your name and email address from these providers. We do not access your Google contacts, GitHub repositories, or other provider data.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">4. How Data Is Stored</h2>
            <ul className="list-disc pl-6 space-y-2">
              <li>Data is stored in a secure PostgreSQL database.</li>
              <li>Receipt files are stored locally on the server filesystem.</li>
              <li>Communication is encrypted via HTTPS in production.</li>
              <li>Session data is stored in server memory (not persistent).</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">5. Data Retention</h2>
            <p>Your data is retained as long as your account exists. When you delete your account, all associated data (expenses, receipts, and personal information) is permanently removed.</p>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">6. Third Parties</h2>
            <p>We do not share, sell, or transfer your data to third parties. The only external services involved are:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>OAuth providers (Google/GitHub) - for authentication only.</li>
              <li>Hosting infrastructure - for data storage and application deployment.</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold mb-4">7. Your Rights</h2>
            <p className="mb-2">You have the right to:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li><strong>Access:</strong> View all your data through the application interface.</li>
              <li><strong>Correction:</strong> Edit your expense entries at any time.</li>
              <li><strong>Deletion:</strong> Delete individual expenses or request account deletion.</li>
              <li><strong>Export:</strong> Contact us to request a copy of your data.</li>
            </ul>
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
