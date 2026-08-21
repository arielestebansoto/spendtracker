import type { Metadata } from "next";
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

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={geist.className}>
        <AuthProvider>
          <div className="min-h-screen flex flex-col">
            <Navbar />
            <main className="flex-1">{children}</main>
          </div>
        </AuthProvider>
      </body>
    </html>
  );
}
