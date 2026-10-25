import { useEffect } from 'react';
import { solveRks } from './core';
export function useWebMCP(go: (page: string) => void, authenticated: boolean) {
  useEffect(() => {
    const context = (navigator as any).modelContext || (document as any).modelContext;
    if (!context?.registerTool) return;
    const navigation = ['home', 'scores', 'song', 'constants', 'leaderboard', 'image', 'more', 'settings', 'calculator', 'custom', 'achievement', 'checkin', 'guess', 'suggestions', 'feedback'];
    const controller = new AbortController();
    const tools = [{
      name: 'navigate_psq', description: 'Open an existing PSQ page visible in the navigation. Requires a signed-in account.',
      inputSchema: { type: 'object', properties: { page: { type: 'string', enum: navigation } }, required: ['page'], additionalProperties: false },
      execute: async ({ page }: { page: string }) => { if (!authenticated || !navigation.includes(page)) throw new Error('Page unavailable'); go(page); return { content: [{ type: 'text', text: 'Opened ' + page }] }; },
    }, {
      name: 'calculate_chart_rks', description: 'Calculate the missing value from two of chart constant, accuracy percentage and chart RKS. Uses the same calculation as the visible RKS calculator.',
      inputSchema: { type: 'object', properties: { constant: { type: 'number' }, accuracy: { type: 'number' }, rks: { type: 'number' } }, additionalProperties: false },
      execute: async (data: any) => ({ content: [{ type: 'text', text: JSON.stringify(solveRks(data.constant ?? null, data.accuracy ?? null, data.rks ?? null)) }] }),
    }];
    const registered: string[] = [];
    for (const tool of tools) { try { const registration = context.registerTool(tool, { signal: controller.signal }); registration?.catch?.(() => {}); registered.push(tool.name); } catch {} }
    return () => { controller.abort(); for (const name of registered) try { context.unregisterTool?.(name); } catch {} };
  }, [go, authenticated]);
}
