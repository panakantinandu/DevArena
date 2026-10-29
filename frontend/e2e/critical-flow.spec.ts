import { expect, test, type Browser, type Page } from '@playwright/test'

/**
 * The highest-priority workflow, end to end against the real backend:
 * register → onboard → complete a daily mission → create a quiz battle → friend joins →
 * both answer → results → peer interview → feedback saved → progress page.
 */

const suffix = Date.now().toString(36)

async function registerAndOnboard(browser: Browser, name: string): Promise<Page> {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto('/register')
  await page.getByLabel('Display name').fill(name)
  await page.getByLabel('Username').fill(`${name.toLowerCase()}_${suffix}`)
  await page.getByLabel('Email').fill(`${name.toLowerCase()}_${suffix}@example.com`)
  await page.getByLabel('Password').fill('correct-horse-battery')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: /let's tune your plan/i })).toBeVisible()
  await page.getByLabel('Daily study time (minutes)').fill('90')
  await page.getByRole('button', { name: 'Start day 1' }).click()
  await expect(page.getByRole('heading', { name: /today's missions/i })).toBeVisible()
  return page
}

test('register → mission → battle → interview → feedback → progress', async ({ browser }) => {
  const alice = await registerAndOnboard(browser, 'Alice')

  // ---- 1. Complete a daily question mission
  await alice.getByRole('link', { name: /data structures/i }).first().click()
  await expect(alice.locator('main').getByRole('radiogroup')).toBeVisible()
  const questionButtons = alice.getByRole('button', { name: /^Question \d+/ })
  const count = await questionButtons.count()
  for (let i = 0; i < count; i++) {
    await questionButtons.nth(i).click()
    await alice.locator('main').getByRole('radio').first().click()
  }
  await alice.getByRole('button', { name: /^Submit/ }).click()
  await expect(alice.getByText(/Mission complete · \+\d+ XP/)).toBeVisible()
  await expect(alice.getByText(/correct$/).first()).toBeVisible()

  // ---- 2. Quiz battle with a second player
  const bob = await registerAndOnboard(browser, 'Bob')
  await alice.goto('/arena')
  await alice.getByLabel('Category').selectOption('JAVA')
  await alice.getByLabel('Questions').fill('3')
  await alice.getByLabel('Seconds per question').fill('15')
  await alice.getByRole('button', { name: 'Create room' }).click()
  const code = (await alice.getByTestId('room-code').innerText()).trim()
  expect(code).toMatch(/^[A-Z0-9]{6}$/)

  await bob.goto(`/arena/join/${code}`)
  await expect(bob.getByRole('heading', { name: /Players/ })).toBeVisible()
  await bob.getByRole('button', { name: "I'm ready" }).click()
  await alice.getByRole('button', { name: "I'm ready" }).click()
  await alice.getByRole('button', { name: 'Start battle' }).click()

  for (let round = 0; round < 3; round++) {
    for (const p of [alice, bob]) {
      await expect(p.getByText(`Question ${round + 1} of 3`)).toBeVisible({ timeout: 30_000 })
      await p.locator('main').getByRole('radio').first().click()
      await p.getByRole('button', { name: 'Lock in answer' }).click()
      // Once everyone has answered the server closes the round at once, so either state is valid.
      await expect(p.getByText(/Locked in|Round \d+ result/).first()).toBeVisible()
    }
  }
  await expect(alice.getByText(/Victory|wins|Draw/).first()).toBeVisible({ timeout: 30_000 })
  await expect(bob.getByText(/Victory|wins|Draw/).first()).toBeVisible({ timeout: 30_000 })
  await expect(alice.getByRole('heading', { name: 'Question breakdown' })).toBeVisible()

  // ---- 3. Peer interview: Alice interviews Bob
  await alice.goto('/interviews')
  await alice.getByLabel('Interview type').selectOption('JAVA_BACKEND')
  await alice.getByLabel('Questions').fill('1')
  await alice.getByRole('button', { name: 'Create private room' }).click()
  const ivCode = (await alice.getByTestId('room-code').innerText()).trim()
  expect(ivCode).toMatch(/^[A-Z0-9]{8}$/)

  await bob.goto(`/interviews/join/${ivCode}`)
  await expect(bob.getByText(/Waiting for Alice to start/)).toBeVisible()
  await alice.getByRole('button', { name: 'Start interview' }).click()

  // Candidate sees the question but never the guide.
  await expect(bob.getByText('Question 1 of 1')).toBeVisible()
  await expect(bob.getByText('Strong answer')).toHaveCount(0)
  // Interviewer sees the guide and can take private notes.
  await expect(alice.getByText('Strong answer')).toBeVisible()
  await alice.getByLabel('Private notes').fill('Clear on basics, vague on resizing.')

  await alice.getByRole('button', { name: 'Finish interview' }).click()
  await alice.getByRole('button', { name: /Confirm finish/ }).click()
  await expect(alice.getByRole('heading', { name: /Score Bob/ })).toBeVisible()
  for (const fieldset of await alice.locator('fieldset').all()) {
    await fieldset.getByRole('button', { name: '4' }).click()
  }
  await alice.getByLabel('Strengths').fill('Structured answers, good examples.')
  await alice.getByLabel('What to improve').fill('Go deeper on internals and edge cases.')
  await alice.getByRole('button', { name: 'Save feedback' }).click()
  await expect(alice.getByText('4.00')).toBeVisible()

  // Candidate gets the report, without the interviewer's private notes.
  await expect(bob.getByText('Structured answers, good examples.')).toBeVisible({ timeout: 30_000 })
  await expect(bob.getByText('vague on resizing')).toHaveCount(0)

  // ---- 4. Progress reflects the activity
  await bob.goto('/progress')
  await expect(bob.getByRole('heading', { name: 'Progress', exact: true })).toBeVisible()
  await expect(bob.getByText('Interview scores (as candidate)')).toBeVisible()
})
