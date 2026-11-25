"use client";

import { useEffect, useState } from "react";

export type ToastVariant = "success" | "error";

type ToastProps = {
  message: string;
  variant?: ToastVariant;
  onDismiss: () => void;
};

export default function Toast({
  message,
  variant = "success",
  onDismiss,
}: ToastProps) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    requestAnimationFrame(() => setVisible(true));
    const timer = setTimeout(() => {
      setVisible(false);
      setTimeout(onDismiss, 300);
    }, 3000);
    return () => clearTimeout(timer);
  }, [onDismiss]);

  const bg = variant === "error" ? "bg-destructive" : "bg-primary";
  const text = variant === "error" ? "text-destructive-foreground" : "text-primary-foreground";

  // Top on a phone, where the bottom belongs to the New spend button; bottom-right on desktop.
  return (
    <div className="fixed top-4 inset-x-4 z-50 md:inset-x-auto md:top-auto md:bottom-6 md:right-6">
      <div
        className={`${bg} ${text} px-4 py-3 rounded-lg shadow-lg text-sm transition-all duration-300 ${
          visible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-2"
        }`}
      >
        {message}
      </div>
    </div>
  );
}
