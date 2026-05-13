/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: 'var(--color-primary)',
          light: 'var(--color-primary-light)',
          surface: 'var(--color-primary-surface)',
        },
        aman: {
          DEFAULT: 'var(--color-aman)',
          bg: 'var(--color-aman-bg)',
        },
        waspada: {
          DEFAULT: 'var(--color-waspada)',
          bg: 'var(--color-waspada-bg)',
        },
        bahaya: {
          DEFAULT: 'var(--color-bahaya)',
          bg: 'var(--color-bahaya-bg)',
        },
        'text-primary': 'var(--color-text-primary)',
        'text-secondary': 'var(--color-text-secondary)',
        surface: 'var(--color-surface)',
        card: 'var(--color-card)',
        divider: 'var(--color-divider)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
