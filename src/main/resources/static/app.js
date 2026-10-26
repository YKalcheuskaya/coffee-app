const cart = new Map();
let idempotencyKey = crypto.randomUUID();
let pendingItems = null;
let submitting = false;
const message = document.querySelector('#message');
const cartElement = document.querySelector('#cart');
const checkout = document.querySelector('#checkout');

function showMessage(text, kind = 'success') { message.textContent = text; message.className = kind; }
function money(value) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value); }
function element(tag, text) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    return node;
}
function renderCart() {
    if (!cart.size) { cartElement.textContent = 'Your cart is empty.'; cartElement.dataset.testid = 'cart-empty'; checkout.disabled = true; return; }
    cartElement.dataset.testid = 'cart';
    const list = element('ul');
    for (const item of cart.values()) list.append(element('li', `${item.name} × ${item.quantity} — ${money(item.unitPrice * item.quantity)}`));
    cartElement.replaceChildren(list);
    checkout.disabled = submitting;
}
async function loadCatalog() {
    const response = await fetch('/api/coffees');
    if (!response.ok) throw new Error('The catalog could not be loaded.');
    const coffees = await response.json();
    const catalog = document.querySelector('#catalog');
    catalog.replaceChildren();
    for (const coffee of coffees) {
        const card = element('article');
        card.dataset.testid = `coffee-${coffee.id}`;
        const row = element('div');
        row.className = 'row';
        const label = element('label', 'Quantity ');
        const input = element('input');
        input.type = 'number'; input.min = '1'; input.max = String(coffee.availableQuantity); input.value = '1';
        input.setAttribute('aria-label', `Quantity for ${coffee.name}`);
        label.append(input);
        const button = element('button', 'Add');
        button.type = 'button';
        button.addEventListener('click', () => {
            // An uncertain checkout must replay the same payload with the same key.
            if (pendingItems) return showMessage('Retry the pending order before changing the cart.', 'error');
            const quantity = Number(input.value);
            if (!Number.isInteger(quantity) || quantity < 1 || quantity > coffee.availableQuantity) return showMessage('Choose an available whole-number quantity.', 'error');
            cart.set(coffee.id, { ...coffee, quantity }); renderCart(); showMessage(`${coffee.name} added to your order.`);
        });
        row.append(label, button);
        card.append(element('h3', coffee.name), element('p', `${coffee.origin} · ${coffee.roastLevel}`), element('p', `${money(coffee.unitPrice)} · ${coffee.availableQuantity} available`), row);
        catalog.append(card);
    }
}
async function findOrder(id) {
    const response = await fetch(`/api/orders/${encodeURIComponent(id)}`);
    if (!response.ok) return showMessage('Order not found.', 'error');
    const order = await response.json();
    const status = element('p', `Order #${order.id}: ${order.status}`);
    status.dataset.testid = 'order-status';
    const details = document.querySelector('#order-details');
    details.replaceChildren(status, element('p', `Total: ${money(order.totalAmount)}`));
    document.querySelector('#order-id').value = order.id;
    if (order.status === 'PLACED') {
        const cancel = element('button', 'Cancel order'); cancel.id = 'cancel-order'; cancel.type = 'button';
        cancel.addEventListener('click', async () => {
            try {
                const cancelled = await fetch(`/api/orders/${order.id}/cancel`, { method: 'POST' });
                if (!cancelled.ok) return showMessage('Order could not be cancelled.', 'error');
                showMessage('Order cancelled and inventory restored.'); await findOrder(order.id);
            } catch { showMessage('Order status could not be loaded. Find the order to check before retrying.', 'error'); }
        });
        details.append(cancel);
    }
}
checkout.addEventListener('click', async () => {
    if (submitting || !cart.size) return;
    submitting = true; renderCart();
    pendingItems ??= [...cart.values()].map(item => ({ coffeeId: item.id, quantity: item.quantity }));
    try {
        const response = await fetch('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey }, body: JSON.stringify({ items: pendingItems }) });
        if (!response.ok) {
            const error = await response.json().catch(() => ({}));
            // A 4xx is a definite rejection; let the user correct the cart.
            if (response.status >= 400 && response.status < 500) { pendingItems = null; idempotencyKey = crypto.randomUUID(); }
            return showMessage(error.message || `Order request failed (${response.status}). Retry your order.`, 'error');
        }
        const order = await response.json();
        cart.clear(); pendingItems = null; idempotencyKey = crypto.randomUUID();
        showMessage(`Order #${order.id} placed.`);
        document.querySelector('#order-id').value = order.id;
        await findOrder(order.id).catch(() => showMessage(`Order #${order.id} placed. Find the order to refresh its status.`));
    } catch {
        showMessage('Order response was not received. Retry without reloading; your cart and retry key are retained.', 'error');
    } finally { submitting = false; renderCart(); }
});
document.querySelector('#find-order').addEventListener('click', () => findOrder(document.querySelector('#order-id').value).catch(() => showMessage('Order status could not be loaded. Try again.', 'error')));
loadCatalog().catch(error => showMessage(error.message, 'error'));
