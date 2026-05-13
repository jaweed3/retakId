import { Component, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';
import { Link } from 'react-router-dom';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('ErrorBoundary caught:', error, info);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.reload();
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-surface p-4">
          <div className="text-center max-w-md">
            <div className="flex h-16 w-16 mx-auto items-center justify-center rounded-2xl bg-bahaya-bg mb-5">
              <AlertTriangle className="h-8 w-8 text-bahaya" />
            </div>
            <h1 className="text-xl font-bold text-text-primary mb-2">Terjadi Kesalahan</h1>
            <p className="text-sm text-text-secondary mb-6">
              Mohon maaf, terjadi kesalahan yang tidak terduga. Silakan muat ulang halaman.
            </p>
            {import.meta.env.DEV && this.state.error && (
              <pre className="text-xs text-left bg-card border border-divider rounded-xl p-4 mb-6 overflow-auto max-h-32 whitespace-pre-wrap">
                {this.state.error.message}
                {'\n'}
                {this.state.error.stack?.split('\n').slice(1, 4).join('\n')}
              </pre>
            )}
            <div className="flex gap-3 justify-center">
              <button
                onClick={this.handleReset}
                className="inline-flex items-center gap-2 rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-md shadow-primary/20 hover:bg-primary-light transition-colors"
              >
                <RefreshCw className="h-4 w-4" />
                Muat Ulang
              </button>
              <Link
                to="/"
                className="inline-flex items-center gap-2 rounded-xl border border-divider bg-card px-5 py-2.5 text-sm font-semibold text-text-secondary hover:text-text-primary transition-colors"
              >
                <Home className="h-4 w-4" />
                Ke Beranda
              </Link>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
