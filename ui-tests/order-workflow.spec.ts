import { expect, test } from '@playwright/test';

test('customer can add coffee, place an order, find it, and cancel it', async ({ page }) => {
  await page.goto('/');
  const espresso = page.getByTestId('coffee-1');
  await expect(espresso).toContainText('Espresso');
  await espresso.getByRole('button', { name: 'Add' }).click();
  await expect(page.getByTestId('cart')).toContainText('Espresso × 1');
  await page.getByRole('button', { name: 'Place order' }).click();
  await expect(page.getByText(/Order #\d+ placed/)).toBeVisible();
  await expect(page.getByTestId('order-status')).toContainText('PLACED');
  await page.getByRole('button', { name: 'Cancel order' }).click();
  await expect(page.getByTestId('order-status')).toContainText('CANCELLED');
});
