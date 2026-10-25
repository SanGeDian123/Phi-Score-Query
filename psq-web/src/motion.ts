import { useCallback, useEffect, useLayoutEffect, useRef } from 'react';

export const motionEase = 'cubic-bezier(.22, 1, .36, 1)';
export const reducedMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

// Only animate the changed region; keep its controls and business state mounted.
export function useContentMotion<T extends HTMLElement>(revision: string, selector?: string) {
  const ref = useRef<T>(null), previous = useRef(revision);
  useLayoutEffect(() => {
    if (previous.current === revision) return;
    previous.current = revision;
    if (!ref.current || reducedMotion()) return;
    const regions = selector ? [...ref.current.querySelectorAll<HTMLElement>(selector)].filter(region => {
      const bounds = region.getBoundingClientRect();
      return bounds.bottom > 0 && bounds.top < innerHeight + 80;
    }) : [ref.current];
    const animations = regions.filter(region => typeof region.animate === 'function').map(region => region.animate(
      [{ opacity: .35, transform: 'translate3d(0,6px,0)' }, { opacity: 1, transform: 'translate3d(0,0,0)' }],
      { duration: 280, easing: motionEase },
    ));
    return () => animations.forEach(animation => animation.cancel());
  }, [revision, selector]);
  return ref;
}

export function useAnimatedClose<T extends HTMLElement>(ref: { current: T | null }, onClose: () => void, panelSelector?: string, disabled = false) {
  const callback = useRef(onClose), closing = useRef(false), animations = useRef<Animation[]>([]), blocked = useRef(disabled);
  callback.current = onClose; blocked.current = disabled;
  useEffect(() => () => { animations.current.forEach(animation => animation.cancel()); }, []);
  return useCallback(() => {
    if (closing.current || blocked.current) return;
    closing.current = true;
    const element = ref.current;
    if (!element || reducedMotion() || typeof element.animate !== 'function') { callback.current(); return; }
    const panel = panelSelector ? element.querySelector<HTMLElement>(panelSelector) : null;
    const opacity = getComputedStyle(element).opacity;
    const panelTransform = panel ? getComputedStyle(panel).transform : '';
    element.inert = true;
    element.dataset.closing = 'true';
    animations.current = [element.animate([{ opacity }, { opacity: 0 }], { duration: 180, easing: motionEase, fill: 'forwards' })];
    if (panel) animations.current.push(panel.animate(
      [{ transform: panelTransform }, { transform: 'translate3d(0,8px,0) scale(.98)' }],
      { duration: 180, easing: motionEase, fill: 'forwards' },
    ));
    Promise.all(animations.current.map(animation => animation.finished)).then(() => callback.current(), () => {});
  }, [ref, panelSelector]);
}
