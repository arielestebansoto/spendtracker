"use client";

const API_URL = process.env.NEXT_PUBLIC_API_URL;

export default function HomePage() {
  const loginGoogle = () => {
    window.location.href =`${API_URL}/oauth2/authorization/google`;
  };

  const loginGithub = () => {
    window.location.href = `${API_URL}/oauth2/authorization/github`;
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Navbar */}
      <header className="border-b">
        <div className="container mx-auto px-6 h-16 flex items-center">
          <a
            href="/"
            className="text-lg font-semibold"
          >
            Spend Tracker AI
          </a>
        </div>
      </header>

      {/* Hero */}
      <main className="flex items-center justify-center px-6">
        <div className="max-w-2xl text-center py-24">
          <h1 className="text-5xl md:text-6xl font-bold tracking-tight">
            Spend Tracker AI
          </h1>

          <p className="mt-6 text-lg text-muted-foreground">
            Understand where your money goes.
          </p>

          <div className="mt-10 flex flex-col sm:flex-row gap-4 justify-center">
            <button
              onClick={loginGoogle}
              className="
                px-6 py-3
                rounded-lg
                border
                text-white
                hover:bg-gray-100
                hover:text-black
                transition
              "
            >
              Continue with Google
            </button>

            <button
              onClick={loginGithub}
              className="
                px-6 py-3
                rounded-lg
                border
                text-white
                hover:bg-gray-100
                hover:text-black
                transition
              "
            >
              Continue with GitHub
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}