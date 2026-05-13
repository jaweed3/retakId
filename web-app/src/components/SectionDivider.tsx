interface SectionDividerProps {
  variant?: 'wave' | 'slant' | 'fade';
  className?: string;
}

function WaveSvg({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 1440 60"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <path
        d="M0 60h1440V0c-196 30-392 44-480 44S676 22 480 10 196 0 0 20v40z"
        fill="currentColor"
      />
    </svg>
  );
}

function SlantSvg({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 1440 64"
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      <polygon points="0,64 1440,0 1440,64" fill="currentColor" />
    </svg>
  );
}

export function SectionDivider({ variant = 'wave', className = '' }: SectionDividerProps) {
  if (variant === 'slant') {
    return (
      <div className={`relative h-16 sm:h-24 -mt-px ${className}`}>
        <SlantSvg className="absolute inset-0 w-full h-full text-primary-surface dark:text-card" />
      </div>
    );
  }

  if (variant === 'fade') {
    return (
      <div className={`h-12 sm:h-16 bg-gradient-to-b from-card to-surface ${className}`} />
    );
  }

  // wave — most prominent for major section breaks
  return (
    <div className={`relative h-16 sm:h-24 -mt-px overflow-hidden ${className}`}>
      <WaveSvg className="absolute bottom-0 w-full h-full text-primary-surface dark:text-card" />
    </div>
  );
}
