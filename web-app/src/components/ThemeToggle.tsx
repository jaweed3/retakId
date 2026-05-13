import { Sun, Moon } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';

export function ThemeToggle() {
  const { theme, toggle } = useTheme();

  return (
    <button
      onClick={toggle}
      className="relative flex h-9 w-9 items-center justify-center rounded-lg text-text-secondary hover:text-text-primary hover:bg-divider/40 transition-colors"
      aria-label={theme === 'light' ? 'Aktifkan mode gelap' : 'Aktifkan mode terang'}
    >
      {theme === 'light' ? <Moon className="h-5 w-5" /> : <Sun className="h-5 w-5" />}
    </button>
  );
}
