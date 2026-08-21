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

  return (
    <div className="fixed bottom-6 right-6 z-50">
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
