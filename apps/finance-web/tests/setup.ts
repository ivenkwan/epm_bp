import '@testing-library/jest-dom/vitest';

// jsdom lacks matchMedia and scroll methods that components and a11y tooling reference.
if (typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {
        return;
      },
      removeListener: () => {
        return;
      },
      addEventListener: () => {
        return;
      },
      removeEventListener: () => {
        return;
      },
      dispatchEvent: () => false,
    }),
  });
}

// jsdom's runtime lacks scrollIntoView even though lib.dom types declare it.
type ScrollableView = { scrollIntoView?: () => void };
const prototype = window.HTMLElement.prototype as unknown as ScrollableView;
if (prototype.scrollIntoView === undefined) {
  Object.defineProperty(window.HTMLElement.prototype, 'scrollIntoView', {
    writable: true,
    value: () => undefined,
  });
}
