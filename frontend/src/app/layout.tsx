import type { Metadata, Viewport } from "next";
import { Geist } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "./components/AuthProvider";
import Navbar from "./components/Navbar";

const geist = Geist({
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Spendtracker",
  description: "Track expenses and manage your personal finances.",
};

// The app has no dark theme. Declaring it light-only stops browsers from darkening it on their own:
// the Google app on iPhone does it with a filter on <body>, and a filter on an ancestor makes
// position: fixed elements (the + button) scroll with the page.
export const viewport: Viewport = {
  colorScheme: "only light",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={geist.className}>
        <AuthProvider>
          {/* dvh, the visible height: on iPhone 100vh (min-h-screen) is the height with the toolbars
              hidden, which gave short pages some 40px of scroll they do not need. */}
          <div className="min-h-dvh flex flex-col">
            <Navbar />
            <main className="flex-1">{children}</main>
          </div>
        </AuthProvider>
      </body>
    </html>
  );
}
