---
name: e2e-browser-tester
description: >-
  Use this skill when creating or running end-to-end browser tests, verifying web UI flows, or testing user interactions with Playwright or Cypress.
license: Apache-2.0
compatibility: requires-playwright
---

# End-to-End (E2E) Browser Testing Runbook

Prescribes standards for creating robust, deterministic end-to-end browser automation suites.

## Playwright Testing Protocol

### 1. Test Architecture & Page Object Model (POM)
- Decouple UI selectors from test assertions.
- Use resilient user-facing locators (`getByRole`, `getByText`, `getByLabel`) rather than fragile CSS classes or XPath.

### 2. Sample Playwright Test
```typescript
import { test, expect } from '@playwright/test';

test.describe('Real-Time Prediction UI Flow', () => {
  test('should display live camera feed and start classification', async ({ page }) => {
    await page.goto('/');
    
    // Check initial state
    await expect(page.getByRole('heading', { name: 'Sign Language Detection' })).toBeVisible();
    
    // Start camera stream
    const startBtn = page.getByRole('button', { name: 'Start Camera' });
    await expect(startBtn).toBeEnabled();
    await startBtn.click();
    
    // Verify streaming canvas appears
    const canvas = page.locator('#video-canvas');
    await expect(canvas).toBeVisible({ timeout: 5000 });
  });
});
```

### 3. Failure Diagnostics
- Configure Playwright to automatically save traces, screenshots, and video on failure:
```javascript
// playwright.config.ts
use: {
  screenshot: 'only-on-failure',
  trace: 'retain-on-failure',
}
```
