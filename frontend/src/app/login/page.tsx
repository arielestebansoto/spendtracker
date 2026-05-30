"use client";

const API_URL =
    process.env.NEXT_PUBLIC_API_URL;

export default function LoginPage() {

    const loginGoogle = () => {
        window.location.href =
            `${API_URL}/oauth2/authorization/google`;
    };

    const loginGithub = () => {
        window.location.href =
            `${API_URL}/oauth2/authorization/github`;
    };

    return (
        <div>

            <button 
                className="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded transition duration-200"
                onClick={loginGoogle}
            >
                Login with Google
            </button>

            <br />
            <br />

            <button 
                className="bg-blue-600 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded transition duration-200"
                onClick={loginGithub}
            >
                Login with GitHub
            </button>
        </div>
    );
}

