import { useId } from "react";

interface LogoMarkProps {
  size?: number;
  className?: string;
  /** Use "light" sobre fundos claros: os nós ganham um tom mais escuro para manter o contraste. */
  tone?: "dark" | "light";
}

/** Balão de conversa formado por nós ligados: a rede distribuída que forma a conversa. */
export function LogoMark({ size = 32, className, tone = "dark" }: LogoMarkProps) {
  const gradientId = useId();
  const node = tone === "light" ? "#5a3fe3" : "#c4b5fd";
  const tail = tone === "light" ? "#7c4dff" : "#a78bfa";
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="4 6 90 94"
      role="img"
      aria-label="Conversa"
    >
      <defs>
        <linearGradient id={gradientId} x1="8" y1="92" x2="92" y2="8" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#7c4dff" />
          <stop offset="1" stopColor="#22e0ee" />
        </linearGradient>
      </defs>
      <path
        d="M23.7 74.3A40 40 0 1 1 45.1 85.4L18 92Z"
        fill="none"
        stroke={`url(#${gradientId})`}
        strokeWidth="5.5"
        strokeLinejoin="round"
      />
      <path
        d="M52 16L76 28 M76 28L83 52 M83 52L67 73 M67 73L40 70 M40 70L23 52 M23 52L30 28 M30 28L52 16 M52 45L52 16 M52 45L76 28 M52 45L67 73 M52 45L40 70 M52 45L23 52 M52 45L30 28 M66 50L76 28 M66 50L83 52 M66 50L67 73 M66 50L52 45 M38 46L23 52 M38 46L30 28 M38 46L52 45 M38 46L40 70"
        fill="none"
        stroke={`url(#${gradientId})`}
        strokeWidth="2.6"
        strokeLinecap="round"
        opacity=".85"
      />
      <circle cx="52" cy="16" r="3.4" fill="#22e0ee" />
      <circle cx="76" cy="28" r="2.6" fill={node} />
      <circle cx="83" cy="52" r="3.4" fill="#22e0ee" />
      <circle cx="67" cy="73" r="2.6" fill={node} />
      <circle cx="40" cy="70" r="2.6" fill={node} />
      <circle cx="23" cy="52" r="2.6" fill={node} />
      <circle cx="30" cy="28" r="2.6" fill={node} />
      <circle cx="52" cy="45" r="2.6" fill={node} />
      <circle cx="66" cy="50" r="2.6" fill={node} />
      <circle cx="38" cy="46" r="2.6" fill={node} />
      <circle cx="18" cy="92" r="4" fill={tail} />
    </svg>
  );
}

export function Wordmark({ size = 32, tone = "dark" }: { size?: number; tone?: "dark" | "light" }) {
  return (
    <span className="wordmark">
      <LogoMark size={size} tone={tone} className="brand-logo" />
      <span className="wordmark-text">conversa</span>
    </span>
  );
}
