import { test, expect } from '@playwright/test';

const casos = [
  {
    senha: 'Senha123',
    confirmacao: 'Senha123',
    esperado: 'Senha cadastrada',
    role: 'status',
    classe: 'limite mínimo de caracteres (8)',
  },
  {
    senha: 'Senha1234567890abcXY',
    confirmacao: 'Senha1234567890abcXY',
    esperado: 'Senha cadastrada',
    role: 'status',
    classe: 'limite máximo de caracteres (20)',
  },
  {
    senha: 'Senha12',
    confirmacao: 'Senha12',
    esperado: 'Senha fora do padrão',
    role: 'alert',
    classe: 'abaixo do mínimo de caracteres (7)',
  },
  {
    senha: 'Senha1234567890abcXYZ',
    confirmacao: 'Senha1234567890abcXYZ',
    esperado: 'Senha fora do padrão',
    role: 'alert',
    classe: 'acima do máximo de caracteres (21)',
  },
  {
    senha: 'senha1234',
    confirmacao: 'senha1234',
    esperado: 'Senha fora do padrão',
    role: 'alert',
    classe: 'sem letra maiúscula',
  },
  {
    senha: 'SENHA1234',
    confirmacao: 'SENHA1234',
    esperado: 'Senha fora do padrão',
    role: 'alert',
    classe: 'sem letra minúscula',
  },
  {
    senha: 'SenhaSegura',
    confirmacao: 'SenhaSegura',
    esperado: 'Senha fora do padrão',
    role: 'alert',
    classe: 'sem número',
  },
  {
    senha: 'Senha 123',
    confirmacao: 'Senha 123',
    esperado: 'Senha fora do padrão',
    role: 'alert',
    classe: 'contém espaço em branco',
  },
  {
    senha: '',
    confirmacao: '',
    esperado: 'Senha fora do padrão',
    role: 'alert',
    classe: 'campos vazios',
  },
  {
    senha: 'Senha123',
    confirmacao: 'Senha124',
    esperado: 'As senhas não coincidem',
    role: 'alert',
    classe: 'confirmação diferente da senha, ambas em formato válido',
  },
  {
    senha: 'Senha123',
    confirmacao: '',
    esperado: 'As senhas não coincidem',
    role: 'alert',
    classe: 'senha válida com confirmação vazia',
  },
];

for (const caso of casos) {
  test(`senha "${caso.senha || '(vazia)'}" — ${caso.classe}`, async ({ page }) => {
    await page.goto('/senha');

    await page.getByLabel('Nova senha').fill(caso.senha);
    await page.getByLabel('Confirmar senha').fill(caso.confirmacao);
    await page.getByRole('button', { name: 'Cadastrar senha' }).click();

    const resultado = page.locator('#resultado');
    await expect(resultado).toBeVisible();
    await expect(resultado).toHaveText(caso.esperado);
    await expect(resultado).toHaveAttribute('role', caso.role);
  });
}
