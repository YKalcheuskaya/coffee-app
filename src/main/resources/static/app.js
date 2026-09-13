const cart = new Map();
let idempotencyKey = crypto.randomUUID();
const message = document.querySelector('#message');
const cartElement = document.querySelector('#cart');
const checkout = document.querySelector('#checkout');

function showMessage(text, kind = 'success') { message.textContent = text; message.className = kind; }
function money(value) { return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value); }
function renderCart() {
    if (!cart.size) { cartElement.textContent = 'Your cart is empty.'; cartElement.dataset.testid = 'cart-empty'; checkout.disabled = true; return; }
    cartElement.dataset.testid = 'cart';
    cartElement.innerHTML = `<ul>${[...cart.values()].map(item => `<li>${item.name} × ${item.quantity} — ${money(item.unitPrice * item.quantity)}</li>`).join('')}</ul>`;
    checkout.disabled = false;
}
async function loadCatalog() {
    const response = await fetch('/api/coffees');
    if (!response.ok) throw new Error('The catalog could not be loaded.');
    const coffees = await response.json();
    document.querySelector('#catalog').innerHTML = coffees.map(coffee => `<article data-testid="coffee-${coffee.id}"><h3>${coffee.name}</h3><p>${coffee.origin} · ${coffee.roastLevel}</p><p>${money(coffee.unitPrice)} · ${coffee.availableQuantity} available</p><div class="row"><label>Quantity <input aria-label="Quantity for ${coffee.name}" type="number" min="1" max="${coffee.availableQuantity}" value="1"></label><button type="button" data-id="${coffee.id}">Add</button></div></article>`).join('');
    document.querySelectorAll('#catalog button').forEach(button => button.addEventListener('click', () => {
        const card = button.closest('article'); const coffee = coffees.find(value => value.id === Number(button.dataset.id));
        const quantity = Number(card.querySelector('input').value);
        if (!Number.isInteger(quantity) || quantity < 1 || quantity > coffee.availableQuantity) return showMessage('Choose an available whole-number quantity.', 'error');
        cart.set(coffee.id, { ...coffee, quantity }); renderCart(); showMessage(`${coffee.name} added to your order.`);
    }));
}
async function findOrder(id) {
    const response = await fetch(`/api/orders/${id}`); const details = document.querySelector('#order-details');
    if (!response.ok) return showMessage('Order not found.', 'error');
    const order = await response.json();
    details.innerHTML = `<p data-testid="order-status">Order #${order.id}: ${order.status}</p><p>Total: ${money(order.totalAmount)}</p>${order.status === 'PLACED' ? '<button id="cancel-order" type="button">Cancel order</button>' : ''}`;
    document.querySelector('#order-id').value = order.id;
    document.querySelector('#cancel-order')?.addEventListener('click', async () => { const cancelled = await fetch(`/api/orders/${order.id}/cancel`, { method: 'POST' }); if (!cancelled.ok) return showMessage('Order could not be cancelled.', 'error'); showMessage('Order cancelled and inventory restored.'); findOrder(order.id); });
}
checkout.addEventListener('click', async () => { checkout.disabled = true; const response = await fetch('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey }, body: JSON.stringify({ items: [...cart.values()].map(item => ({ coffeeId: item.id, quantity: item.quantity })) }) }); if (!response.ok) { checkout.disabled = false; const error = await response.json(); return showMessage(error.message || `Order request failed (${response.status}).`, 'error'); } const order = await response.json(); cart.clear(); renderCart(); idempotencyKey = crypto.randomUUID(); showMessage(`Order #${order.id} placed.`); findOrder(order.id); });
document.querySelector('#find-order').addEventListener('click', () => findOrder(document.querySelector('#order-id').value));
loadCatalog().catch(error => showMessage(error.message, 'error'));
