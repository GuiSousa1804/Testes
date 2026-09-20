import { test, expect } from '@playwright/test';

const casos = [
  {
    cep: '87000000',
    valor: '50,00',
    esperado: 'Frete: R$ 15,00',
    role: 'status',
    classe: 'CEP iniciado por 8, valor abaixo do limite de frete grátis',
  },
  {
    cep: '87000000',
    valor: '199,99',
    esperado: 'Frete: R$ 15,00',
    role: 'status',
    classe: 'valor no limite inferior, um centavo abaixo de R$ 200,00',
  },
  {
    cep: '87000000',
    valor: '200,00',
    esperado: 'Frete grátis',
    role: 'status',
    classe: 'valor no limite exato de R$ 200,00 (fronteira)',
  },
  {
    cep: '87000000',
    valor: '200,01',
    esperado: 'Frete grátis',
    role: 'status',
    classe: 'valor um centavo acima do limite de R$ 200,00',
  },
  {
    cep: '12345678',
    valor: '50,00',
    esperado: 'Frete: R$ 25,00',
    role: 'status',
    classe: 'CEP não iniciado por 8, valor abaixo do limite de frete grátis',
  },
  {
    cep: '12345678',
    valor: '200,00',
    esperado: 'Frete grátis',
    role: 'status',
    classe: 'CEP não iniciado por 8, valor no limite de frete grátis',
  },
  {
    cep: '1234567',
    valor: '50,00',
    esperado: 'Dados inválidos',
    role: 'alert',
    classe: 'CEP com menos de 8 dígitos',
  },
  {
    cep: '123456789',
    valor: '50,00',
    esperado: 'Dados inválidos',
    role: 'alert',
    classe: 'CEP com mais de 8 dígitos',
  },
  {
    cep: '1234567a',
    valor: '50,00',
    esperado: 'Dados inválidos',
    role: 'alert',
    classe: 'CEP com caractere não numérico',
  },
  {
    cep: '',
    valor: '50,00',
    esperado: 'Dados inválidos',
    role: 'alert',
    classe: 'CEP vazio',
  },
  {
    cep: '12345678',
    valor: '0',
    esperado: 'Dados inválidos',
    role: 'alert',
    classe: 'valor igual a zero (deve ser maior que zero)',
  },
  {
    cep: '12345678',
    valor: '',
    esperado: 'Dados inválidos',
    role: 'alert',
    classe: 'valor do pedido vazio',
  },
  {
    cep: '12345678',
    valor: '10,999',
    esperado: 'Dados inválidos',
    role: 'alert',
    classe: 'valor com mais de duas casas decimais',
  },
  {
    cep: '12345678',
    valor: '10.50',
    esperado: 'Frete: R$ 25,00',
    role: 'status',
    classe: 'valor com ponto como separador decimal (formato aceito)',
  },
];

for (const caso of casos) {
  test(`CEP "${caso.cep || '(vazio)'}" / valor "${caso.valor || '(vazio)'}" — ${caso.classe}`, async ({ page }) => {
    await page.goto('/frete');

    await page.getByLabel('CEP').fill(caso.cep);
    await page.getByLabel('Valor do pedido').fill(caso.valor);
    await page.getByRole('button', { name: 'Calcular frete' }).click();

    const resultado = page.locator('#resultado');
    await expect(resultado).toBeVisible();
    await expect(resultado).toHaveText(caso.esperado);
    await expect(resultado).toHaveAttribute('role', caso.role);
  });
}
