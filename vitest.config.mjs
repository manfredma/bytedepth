import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    coverage: {
      include: [
        'bytedepth-start/src/main/resources/static/js/post-image-lightbox.js',
        'bytedepth-start/src/main/resources/static/js/annotation-document.js',
        'bytedepth-start/src/main/resources/static/js/annotation-display.js',
        'bytedepth-start/src/main/resources/static/js/annotation-render-lifecycle.js',
        'bytedepth-start/src/main/resources/static/js/post-mermaid.js',
        'bytedepth-start/src/main/resources/static/js/code-blocks.js',
        'bytedepth-start/src/main/resources/static/js/nav-menu.js',
        'bytedepth-start/src/main/resources/static/js/reading-history-page.js'
      ],
      provider: 'v8',
      reporter: ['text'],
      thresholds: {
        branches: 100,
        functions: 100,
        lines: 100,
        statements: 100
      }
    },
    environment: 'happy-dom',
    globals: true,
    include: [
      '**/__tests__/**/*.test.js',
      '**/src/test/js/**/*.test.js'
    ],
    setupFiles: ['./jest.setup.js']
  }
});
