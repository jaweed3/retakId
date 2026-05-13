import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

export function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    // Scroll the main content area to top
    const main = document.querySelector('[data-main-content]');
    if (main) {
      main.scrollTop = 0;
    }
  }, [pathname]);

  return null;
}
