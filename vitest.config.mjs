import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    coverage: {
      include: ['bytedepth-start/src/main/resources/static/js/post-image-lightbox.js'],
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
