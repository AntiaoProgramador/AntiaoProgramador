const output = document.getElementById('output');

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || 'Erro na requisição');
  }
  return data;
}

function formDataToJson(form) {
  return Object.fromEntries(new FormData(form).entries());
}

async function refreshInvoices() {
  const data = await request('/api/invoices');
  output.textContent = JSON.stringify(data, null, 2);
}

document.getElementById('invoice-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    const payload = formDataToJson(e.target);
    payload.totalAmount = Number(payload.totalAmount);
    await request('/api/invoices', { method: 'POST', body: JSON.stringify(payload) });
    e.target.reset();
    await refreshInvoices();
  } catch (err) {
    alert(err.message);
  }
});

document.getElementById('confirm-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    const payload = formDataToJson(e.target);
    const id = payload.invoiceId;
    delete payload.invoiceId;
    await request(`/api/invoices/${id}/confirm`, { method: 'POST', body: JSON.stringify(payload) });
    e.target.reset();
    await refreshInvoices();
  } catch (err) {
    alert(err.message);
  }
});

document.getElementById('disagree-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    const payload = formDataToJson(e.target);
    const id = payload.invoiceId;
    delete payload.invoiceId;
    await request(`/api/invoices/${id}/disagree`, { method: 'POST', body: JSON.stringify(payload) });
    e.target.reset();
    await refreshInvoices();
  } catch (err) {
    alert(err.message);
  }
});

document.getElementById('refresh').addEventListener('click', refreshInvoices);

refreshInvoices().catch((err) => {
  output.textContent = err.message;
});
