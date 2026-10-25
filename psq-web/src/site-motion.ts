import { useEffect, type RefObject } from 'react';

// Viewport and pointer work is event-driven; no render loop runs while idle.
export function useSiteMotion(ref: RefObject<HTMLDivElement | null>) {
  useEffect(() => {
    const root = ref.current;
    if (!root) return;
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const fine = matchMedia('(hover: hover) and (pointer: fine)');
    const compact = matchMedia('(max-width: 800px), (pointer: coarse)');
    const progressBar = root.querySelector<HTMLElement>('.site-reading-progress');
    const hero = root.querySelector<HTMLElement>('.site-hero');
    const sections = Array.from(root.querySelectorAll<HTMLElement>('section[id]'));
    const navigation = Array.from(root.querySelectorAll<HTMLElement>('[data-section-link]'));
    const topButton = root.querySelector<HTMLButtonElement>('.site-back-top');
    let scrollFrame = 0, pointerFrame = 0;
    let lastProgress = -1, lastLift = '';
    let lastScrolled: boolean | undefined, lastTopVisible: boolean | undefined, lastActive: string | undefined;
    let point: { x: number; y: number; target: Element } | null = null;
    let lastSpot: HTMLElement | null = null, lastMagnet: HTMLElement | null = null;
    const watched = new WeakSet<Element>();
    const reveal = new IntersectionObserver(entries => {
      for (const entry of entries) if (entry.isIntersecting) {
        entry.target.classList.add('is-revealed');
        reveal.unobserve(entry.target);
      }
    }, { threshold: .08, rootMargin: '0px 0px -35px 0px' });
    const register = (scope: Element) => {
      const elements = scope.matches('[data-reveal]') ? [scope, ...scope.querySelectorAll('[data-reveal]')] : scope.querySelectorAll('[data-reveal]');
      for (const element of elements) if (!watched.has(element)) {
        watched.add(element);
        if (reduced.matches) element.classList.add('is-revealed');
        else reveal.observe(element);
      }
    };
    register(root);
    root.dataset.motion = 'ready';
    const mutations = new MutationObserver(records => {
      for (const record of records) for (const node of record.addedNodes) if (node instanceof Element) register(node);
    });
    mutations.observe(root, { childList: true, subtree: true });
    const heroVisibility = new IntersectionObserver(entries => {
      root.dataset.heroVisible = String(entries[0]?.isIntersecting ?? false);
    });
    if (hero) heroVisibility.observe(hero);

    const resetPointer = () => {
      hero?.style.setProperty('--pointer-x', '0px');
      hero?.style.setProperty('--pointer-y', '0px');
      hero?.style.setProperty('--tilt-x', '0deg');
      hero?.style.setProperty('--tilt-y', '0deg');
      lastSpot?.style.setProperty('--spot-opacity', '0');
      lastMagnet?.style.setProperty('--magnet-x', '0px');
      lastMagnet?.style.setProperty('--magnet-y', '0px');
      lastSpot = lastMagnet = null;
    };
    const updateScroll = () => {
      scrollFrame = 0;
      const max = document.documentElement.scrollHeight - innerHeight;
      const progress = Math.round((max > 0 ? Math.max(0, Math.min(1, scrollY / max)) : 0) * 1000) / 1000;
      const scrolled = scrollY > 24, visible = scrollY > 650;
      const lift = reduced.matches || compact.matches || root.dataset.heroVisible === 'false' ? '0px' : Math.min(scrollY * .07, 34) + 'px';
      let active = '';
      for (const section of sections) if (section.getBoundingClientRect().top <= innerHeight * .36) active = section.id;
      // Read geometry first, then update only local properties that changed.
      // A root custom property would invalidate inherited styles across the page.
      if (progress !== lastProgress) {
        if (progressBar) progressBar.style.transform = 'scaleX(' + progress + ')';
        lastProgress = progress;
      }
      if (scrolled !== lastScrolled) { root.toggleAttribute('data-scrolled', scrolled); lastScrolled = scrolled; }
      if (hero && lift !== lastLift) { hero.style.setProperty('--scroll-lift', lift); lastLift = lift; }
      if (active !== lastActive) {
        for (const link of navigation) {
          if (link.dataset.sectionLink === active) link.setAttribute('aria-current', 'location');
          else link.removeAttribute('aria-current');
        }
        lastActive = active;
      }
      if (topButton && visible !== lastTopVisible) {
        topButton.toggleAttribute('data-visible', visible);
        topButton.setAttribute('aria-hidden', String(!visible));
        topButton.tabIndex = visible ? 0 : -1;
        lastTopVisible = visible;
      }
    };
    const scroll = () => { if (!scrollFrame) scrollFrame = requestAnimationFrame(updateScroll); };
    const move = (event: PointerEvent) => {
      if (event.pointerType !== 'mouse' || reduced.matches || compact.matches || !fine.matches || document.hidden || !(event.target instanceof Element)) return;
      point = { x: event.clientX, y: event.clientY, target: event.target };
      if (pointerFrame) return;
      pointerFrame = requestAnimationFrame(() => {
        pointerFrame = 0;
        if (!point) return;
        const { x, y, target } = point;
        if (hero) {
          const bounds = hero.getBoundingClientRect();
          if (y >= bounds.top && y <= bounds.bottom && x >= bounds.left && x <= bounds.right) {
            const dx = (x - bounds.left) / bounds.width - .5, dy = (y - bounds.top) / bounds.height - .5;
            hero.style.setProperty('--pointer-x', (dx * 24).toFixed(2) + 'px');
            hero.style.setProperty('--pointer-y', (dy * 18).toFixed(2) + 'px');
            hero.style.setProperty('--tilt-x', (-dy * 7).toFixed(2) + 'deg');
            hero.style.setProperty('--tilt-y', (dx * 9).toFixed(2) + 'deg');
          } else if (hero.style.getPropertyValue('--pointer-x') !== '0px') resetPointer();
        }
        const spot = target.closest<HTMLElement>('[data-spotlight]');
        if (lastSpot !== spot) lastSpot?.style.setProperty('--spot-opacity', '0');
        if (spot) {
          const bounds = spot.getBoundingClientRect();
          spot.style.setProperty('--spot-x', (x - bounds.left) + 'px');
          spot.style.setProperty('--spot-y', (y - bounds.top) + 'px');
          spot.style.setProperty('--spot-opacity', '1');
        }
        lastSpot = spot;
        const magnet = target.closest<HTMLElement>('[data-magnetic]');
        if (lastMagnet !== magnet) {
          lastMagnet?.style.setProperty('--magnet-x', '0px');
          lastMagnet?.style.setProperty('--magnet-y', '0px');
        }
        if (magnet) {
          const bounds = magnet.getBoundingClientRect();
          magnet.style.setProperty('--magnet-x', ((x - bounds.left - bounds.width / 2) * .07).toFixed(2) + 'px');
          magnet.style.setProperty('--magnet-y', ((y - bounds.top - bounds.height / 2) * .12).toFixed(2) + 'px');
        }
        lastMagnet = magnet;
      });
    };
    const visibility = () => { root.dataset.pageVisible = String(!document.hidden); if (document.hidden) resetPointer(); };
    const preference = () => {
      resetPointer();
      root.dataset.motionProfile = compact.matches ? 'compact' : 'full';
      if (reduced.matches) for (const element of root.querySelectorAll('[data-reveal]')) element.classList.add('is-revealed');
      updateScroll();
    };
    root.dataset.motionProfile = compact.matches ? 'compact' : 'full';
    updateScroll(); visibility();
    window.addEventListener('scroll', scroll, { passive: true });
    window.addEventListener('resize', scroll, { passive: true });
    root.addEventListener('pointermove', move, { passive: true });
    root.addEventListener('pointerleave', resetPointer);
    root.addEventListener('pointercancel', resetPointer);
    document.addEventListener('visibilitychange', visibility);
    reduced.addEventListener('change', preference);
    compact.addEventListener('change', preference);
    fine.addEventListener('change', resetPointer);
    return () => {
      reveal.disconnect(); mutations.disconnect(); heroVisibility.disconnect();
      cancelAnimationFrame(scrollFrame); cancelAnimationFrame(pointerFrame);
      window.removeEventListener('scroll', scroll); window.removeEventListener('resize', scroll);
      root.removeEventListener('pointermove', move); root.removeEventListener('pointerleave', resetPointer); root.removeEventListener('pointercancel', resetPointer);
      document.removeEventListener('visibilitychange', visibility);
      reduced.removeEventListener('change', preference); compact.removeEventListener('change', preference); fine.removeEventListener('change', resetPointer);
      delete root.dataset.motion;
      delete root.dataset.motionProfile;
    };
  }, [ref]);
}
