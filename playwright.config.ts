import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './ui-tests',
  workers: 1,
  use: { baseURL: 'http://127.0.0.1:8085', browserName: 'chromium' },
  webServer: {
    command: 'mvn --batch-mode -Dspring-boot.run.profiles=ui -Dspring-boot.run.arguments=--server.port=8085 spring-boot:run',
    url: 'http://127.0.0.1:8085',
    reuseExistingServer: false,
    timeout: 120000
  }
});
