import { expect, test } from '@playwright/test'

/** Plays a full daily Brain Gym run through the UI: every puzzle type, confidence, scoring and review. */
test('brain gym daily run', async ({ page }) => {
  const s = Date.now().toString(36)
  await page.goto('/register')
  await page.getByLabel('Display name').fill('Gym Tester')
  await page.getByLabel('Username').fill(`gym_${s}`)
  await page.getByLabel('Email').fill(`gym_${s}@example.com`)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.getByRole('button', { name: 'Start day 1' }).click()
  await expect(page.getByRole('heading', { name: /today's missions/i })).toBeVisible()

  await page.getByRole('link', { name: 'Brain gym' }).first().click()
  await page.getByRole('button', { name: /start daily/i }).click()
  const main = page.locator('main')

  for (let i = 0; i < 5; i++) {
    await expect(main.getByText(`puzzle ${i + 1} of 5`)).toBeVisible()
    const badge = await main.locator('header').first().innerText()
    if (/Estimation/i.test(badge)) {
      await main.getByLabel('Best guess').fill('20k')
      await main.getByLabel(/^Low/).fill('2k')
      await main.getByLabel(/^High/).fill('200k')
      await main.getByRole('button', { name: 'Lock in estimate' }).click()
    } else if (/Incident/i.test(badge)) {
      await main.getByRole('button', { name: /Reveal/ }).first().click()
      await expect(main.getByText(/clue cost so far: −[1-9]/)).toBeVisible()
      await main.getByRole('radiogroup', { name: 'Root cause' }).getByRole('radio').nth(1).click()
      await main.getByRole('radiogroup', { name: 'First action' }).getByRole('radio').first().click()
      await main.getByRole('button', { name: 'Submit diagnosis' }).click()
    } else if (/Spot the flaw/i.test(badge)) {
      await main.getByRole('button', { name: /^Line 3[,:]/ }).click()
      await main.getByRole('button', { name: /^Line 7[,:]/ }).click()
      await main.getByRole('button', { name: /Submit review/ }).click()
    } else {
      await main.getByRole('radiogroup', { name: 'Your design' }).getByRole('radio').nth(1).click()
      await main.getByRole('radiogroup', { name: /main risk/ }).getByRole('radio').first().click()
      await main.getByRole('button', { name: 'Commit decision' }).click()
    }
    // Every answer reveals a verdict with the score and an explanation.
    await expect(main.getByText(/\/100$/).first()).toBeVisible()
    if (i < 4) await main.getByRole('button', { name: /^Next/ }).click()
  }
  await expect(main.getByText(/complete$/i)).toBeVisible()

  // The hub now shows the completed daily, skill profile and calibration.
  await page.getByRole('link', { name: 'Brain gym' }).first().click()
  await expect(page.getByRole('button', { name: /review today/i })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Calibration' })).toBeVisible()
  await expect(page.getByText(/answers$/).first()).toBeVisible()
})
