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

  const orderId = await page.locator('#order-id').inputValue();
  await page.reload();
  await page.locator('#order-id').fill(orderId);
  await page.getByRole('button', { name: 'Find order' }).click();
  await expect(page.getByTestId('order-status')).toContainText('PLACED');

  await page.getByRole('button', { name: 'Cancel order' }).click();
  await expect(page.getByTestId('order-status')).toContainText('CANCELLED');
});

test('customer cannot add a nonpositive quantity to the cart', async ({ page }) => {
  await page.goto('/');
  const espresso = page.getByTestId('coffee-1');
  await page.getByLabel('Quantity for Espresso').fill('0');
  await espresso.getByRole('button', { name: 'Add' }).click();

  await expect(page.getByRole('status')).toHaveText('Choose an available whole-number quantity.');
  await expect(page.getByTestId('cart-empty')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Place order' })).toBeDisabled();
});
