import { execSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import { appointmentRow, book, expect, jobCardHeading, nextDay, openJobCardFromRow, seedCustomer, slot, test } from './support/bayflow';

/**
 * Check-in while workshop-service is down: the check-in still succeeds, the job-card request
 * waits in appointment-service's outbox, and is delivered (exactly once) when the workshop is
 * back. Stops and starts the workshop-service container, so it needs the Docker CLI; it is
 * skipped otherwise, or when E2E_SKIP_CHAOS=1.
 */

const repoRoot = fileURLToPath(new URL('../..', import.meta.url));

function compose(args: string): string {
  return execSync(`docker compose ${args}`, { cwd: repoRoot, stdio: 'pipe', timeout: 180_000 }).toString();
}

function dockerAvailable(): boolean {
  try {
    compose('ps --services');
    return true;
  } catch {
    return false;
  }
}

test.describe('Resilience', () => {
  test.skip(process.env.E2E_SKIP_CHAOS === '1' || !dockerAvailable(), 'Needs the Docker CLI and the compose stack');

  test('check-in while the workshop service is down: the job card arrives later, once', async ({ cast }) => {
    test.setTimeout(480_000);
    const seeded = await seedCustomer(cast);
    const at = slot(nextDay(), 12);
    const appointment = await book(seeded.api, seeded.vehicle.id, at);
    const advisorApi = await cast.api('advisor1');
    await advisorApi.post(`/api/v1/appointments/${appointment.id}/confirm`);
    await advisorApi.post(`/api/v1/appointments/${appointment.id}/arrive`);

    const advisor = await cast.actor('Service advisor');
    await advisor.signIn('advisor1');
    const row = await appointmentRow(advisor, at.date, seeded.vehicle.registration);

    try {
      await cast.note('Stopping the workshop-service container', 'edge');
      compose('stop workshop-service');

      await advisor.edge('Checks the car in while the workshop service is unreachable');
      await row.getByRole('button', { name: 'Check in' }).click();
      const dialog = advisor.page.getByRole('dialog', { name: 'Check in vehicle' });
      await dialog.getByLabel('Odometer reading (miles)').fill(String(seeded.vehicle.mileage + 300));
      await dialog.getByRole('button', { name: 'Check in' }).click();
      await advisor.expectToast('Checked in. The job card will open shortly.');
      const after = await appointmentRow(advisor, at.date, seeded.vehicle.registration);
      await expect(after).toContainText('Checked in');
      await expect(after.getByText('Job card opening…')).toBeVisible();
      await advisor.check('The check-in succeeded; the job card request is waiting in the outbox');
    } finally {
      await cast.note('Starting workshop-service again', 'step');
      compose('start workshop-service');
    }

    await advisor.say('Waits for the outbox to retry (backoff 10 s, doubling)');
    await expect
      .poll(async () => (await advisorApi.get<{ jobCardId?: string }>(`/api/v1/appointments/${appointment.id}`)).jobCardId, {
        timeout: 360_000,
        intervals: [5_000],
        message: 'job card delivered from the outbox',
      })
      .toBeTruthy();

    await openJobCardFromRow(advisor, at.date, seeded.vehicle.registration);
    await expect(jobCardHeading(advisor)).toContainText('Open');
    await advisor.check('The job card opened by itself once the workshop was back');

    const cards = await advisorApi.get<{ content: { registration: string }[] }>('/api/v1/job-cards?status=OPEN&size=200');
    expect(cards.content.filter((c) => c.registration === seeded.vehicle.registration)).toHaveLength(1);
    await cast.note('Exactly one job card exists for the appointment: retries are idempotent');
  });
});
