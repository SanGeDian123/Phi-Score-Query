import { Component, useEffect, type ReactNode } from 'react';

function startupReady() {
  document.getElementById('root')?.setAttribute('data-psq-ready', 'true');
  document.dispatchEvent(new Event('psq:ready'));
}

export function StartupReady({ children }: { children: ReactNode }) {
  useEffect(startupReady, []);
  return children;
}

export class StartupBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { startupReady(); }
  render() {
    if (!this.state.failed) return this.props.children;
    return <main className="psq-startup" role="alert"><section className="psq-startup-card">
      <img src="/icons/icon-192.png" width="64" height="64" alt="" />
      <h1>暂时无法打开 PSQ</h1>
      <p>页面加载遇到问题，请检查网络后重新加载。若问题持续，请更新浏览器，并确认内容拦截器允许本站运行脚本。</p>
      <a href="">重新加载</a>
    </section></main>;
  }
}
