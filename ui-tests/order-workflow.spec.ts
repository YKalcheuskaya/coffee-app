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

test('catalog and cart display stored markup and quotes as text', async ({ page, request }) => {
  const name = `Quote " <img src=x onerror="window.catalogXss=1">`;
  const origin = '<img src=x onerror="window.catalogXss=2">';
  const response = await request.post('/api/coffees', { data: { name, origin, roastLevel: 'MEDIUM', unitPrice: 4, availableQuantity: 2 } });
  expect(response.status()).toBe(201);
  const coffee = await response.json();
  try {
    await page.goto('/');
    const card = page.getByTestId(`coffee-${coffee.id}`);
    await expect(card.getByRole('heading')).toHaveText(name);
    await expect(card).toContainText(origin);
    await expect(page.getByLabel(`Quantity for ${name}`, { exact: true })).toHaveValue('1');
    await card.getByRole('button', { name: 'Add' }).click();
    await expect(page.getByTestId('cart')).toContainText(name);
    expect(await page.locator('#catalog img, #cart img').count()).toBe(0);
    expect(await page.evaluate(() => (window as any).catalogXss)).toBeUndefined();
  } finally { expect((await request.delete(`/api/coffees/${coffee.id}`)).status()).toBe(204); }
});

for (const lostResponse of [false, true]) {
  test(`checkout safely retries after ${lostResponse ? 'losing a successful response' : 'a transport failure'}`, async ({ page, request }) => {
    const before = await (await request.get('/api/coffees/1')).json();
    let originalOrder: any;
    const requests: { key: string | undefined, body: string | null }[] = [];
    await page.route('**/api/orders', async route => {
      requests.push({ key: route.request().headers()['idempotency-key'], body: route.request().postData() });
      if (requests.length === 1 && !lostResponse) return route.abort('failed');
      const response = await route.fetch();
      expect(response.status()).toBe(requests.length > 1 && lostResponse ? 200 : 201);
      const order = await response.json();
      if (requests.length === 1) { originalOrder = order; return route.abort('failed'); }
      if (lostResponse) expect(order.id).toBe(originalOrder.id);
      await route.fulfill({ response });
    });
    await page.goto('/');
    await page.getByTestId('coffee-1').getByRole('button', { name: 'Add' }).click();
    await page.getByRole('button', { name: 'Place order' }).click();
    await expect(page.getByRole('status')).toContainText('Retry without reloading');
    await expect(page.getByTestId('cart')).toContainText('Espresso × 1');
    await expect(page.getByRole('button', { name: 'Place order' })).toBeEnabled();
    await page.getByTestId('coffee-2').getByRole('button', { name: 'Add' }).click();
    await expect(page.getByRole('status')).toContainText('Retry the pending order');
    await page.getByRole('button', { name: 'Place order' }).click();
    await expect(page.getByTestId('order-status')).toContainText('PLACED');
    if (lostResponse) await expect(page.getByTestId('order-status')).toContainText(`Order #${originalOrder.id}:`);
    expect(requests).toHaveLength(2);
    expect(requests[1]).toEqual(requests[0]);
    const after = await (await request.get('/api/coffees/1')).json();
    expect(after.availableQuantity).toBe(before.availableQuantity - 1);
    await page.getByRole('button', { name: 'Cancel order' }).click();
    await expect(page.getByTestId('order-status')).toContainText('CANCELLED');
  });
}
