import { expect, test, type Page } from '@playwright/test'

// Visual review helper: `SCREENSHOTS=dir npx playwright test screens` saves key screens in both themes.
test.skip(!process.env.SCREENSHOTS, 'Set SCREENSHOTS=<dir> to capture screens')

async function setTheme(page: Page, mode: 'light' | 'dark') {
  await page.evaluate((m) => localStorage.setItem('devarena-theme', m), mode)
  await page.reload()
}

test('capture key screens', async ({ page }) => {
  const dir = process.env.SCREENSHOTS!
  await page.goto('/login')
  for (const mode of ['light', 'dark'] as const) {
    await setTheme(page, mode)
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
    await page.screenshot({ path: `${dir}/${mode}-01-login.png` })
  }
  const s = Date.now().toString(36)
  await setTheme(page, 'light')
  await page.goto('/register')
  await page.getByLabel('Display name').fill('Priya Raman')
  await page.getByLabel('Username').fill(`priya_${s}`)
  await page.getByLabel('Email').fill(`priya_${s}@example.com`)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: /tune your plan/i })).toBeVisible()
  await page.getByRole('button', { name: 'Start day 1' }).click()
  await expect(page.getByRole('heading', { name: /today's missions/i })).toBeVisible()

  for (const mode of ['light', 'dark'] as const) {
    await setTheme(page, mode)
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /today's missions/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /data structures/i }).first()).toBeVisible()
    await page.screenshot({ path: `${dir}/${mode}-02-dashboard.png` })
    await page.getByRole('link', { name: /data structures/i }).first().click()
    await expect(page.locator('main').getByRole('radiogroup')).toBeVisible()
    await page.locator('main').getByRole('radio').nth(1).click()
    await page.screenshot({ path: `${dir}/${mode}-03-mission.png` })
    await page.goto('/arena')
    await page.getByRole('button', { name: 'Create room' }).click()
    await expect(page.getByTestId('room-code')).toBeVisible()
    await page.screenshot({ path: `${dir}/${mode}-04-battle-lobby.png` })
    await page.getByRole('button', { name: /cancel room/i }).click()
  }
  await page.setViewportSize({ width: 390, height: 844 })
  await setTheme(page, 'light')
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /today's missions/i })).toBeVisible()
  await page.screenshot({ path: `${dir}/light-05-mobile.png` })
})

test('capture brain gym screens', async ({ page }) => {
  const dir = process.env.SCREENSHOTS!
  const s = Date.now().toString(36)
  await page.goto('/register')
  await page.getByLabel('Display name').fill('Arjun Mehta')
  await page.getByLabel('Username').fill(`arjun_${s}`)
  await page.getByLabel('Email').fill(`arjun_${s}@example.com`)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.getByRole('button', { name: 'Start day 1' }).click()
  await expect(page.getByRole('heading', { name: /today's missions/i })).toBeVisible()
  await page.goto('/gym')
  await page.getByRole('button', { name: 'Practice 4' }).nth(2).click()
  const main = page.locator('main')
  await expect(main.getByText('puzzle 1 of 4')).toBeVisible()
  await main.getByRole('button', { name: /^Line 3[,:]/ }).click()
  await main.getByRole('button', { name: /^Line 5[,:]/ }).click()
  await page.screenshot({ path: `${dir}/gym-review-open.png`, fullPage: true })
  await main.getByRole('button', { name: /Submit review/ }).click()
  await expect(main.getByText(/\/100$/).first()).toBeVisible()
  await page.screenshot({ path: `${dir}/gym-review-verdict.png`, fullPage: true })
  await page.goto('/gym')
  await expect(page.getByRole('heading', { name: 'Calibration' })).toBeVisible()
  await page.screenshot({ path: `${dir}/gym-hub.png`, fullPage: true })
})
