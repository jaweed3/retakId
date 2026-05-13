interface SectionDividerProps {
  variant?: 'wave' | 'slant' | 'fade';
  className?: string;
}

function WaveSvg({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 1440 48"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <path
        d="M0 48h1440V0c-196 26-392 38-480 38S676 18 480 8 196 0 0 16v32z"
        fill="currentColor"
      />
    </svg>
  );
}

function SlantSvg({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 1440 48"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <polygon points="0,48 1440,0 1440,48" fill="currentColor" />
    </svg>
  );
}

export function SectionDivider({ variant = 'wave', className = '' }: SectionDividerProps) {
  if (variant === 'slant') {
    return (
      <div className={`relative h-12 sm:h-16 -mt-px ${className}`}>
        <SlantSvg className="absolute inset-0 w-full h-full text-card" />
      </div>
    );
  }

  if (variant === 'fade') {
    return (
      <div className={`h-8 sm:h-12 bg-gradient-to-b from-card to-surface ${className}`} />
    );
  }

  // wave (default) — most prominent for major section breaks
  return (
    <div className={`relative h-12 sm:h-16 -mt-px overflow-hidden ${className}`}>
      <WaveSvg className="absolute bottom-0 w-full h-full text-card" />
    </div>
  );
}
