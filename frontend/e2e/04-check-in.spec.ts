import { appointmentRow, book, expect, jobCardHeading, nextDay, openJobCardFromRow, seedCustomer, slot, test } from './support/bayflow';

/** The advisor's side of an appointment: concurrency, odometer rules and status rules. */
test.describe('Check-in', () => {
  test('two advisors act on the same booking at the same time', async ({ cast }) => {
    const seeded = await seedCustomer(cast);
    const at = slot(nextDay(), 9);
    await book(seeded.api, seeded.vehicle.id, at);

    const front = await cast.actor('Advisor at the front desk');
    await front.signIn('advisor1');
    const phone = await cast.actor('Advisor on the phone');
    await phone.signIn('advisor1');

    await front.say('Both advisors have the booking open');
    const frontRow = await appointmentRow(front, at.date, seeded.vehicle.registration);
    const phoneRow = await appointmentRow(phone, at.date, seeded.vehicle.registration);
    await expect(frontRow.getByRole('button', { name: 'Confirm' })).toBeVisible();
    await expect(phoneRow.getByRole('button', { name: 'Confirm' })).toBeVisible();

    await front.say('Confirms it');
    await frontRow.getByRole('button', { name: 'Confirm' }).click();
    await front.expectToast('Confirmed');

    await phone.edge('Still sees the old screen and presses Confirm too');
    await phoneRow.getByRole('button', { name: 'Confirm' }).click();
    await phone.expectToast(/Cannot .* appointment that is CONFIRMED/);
    await phone.check('Refused with 409; nothing changed twice');
    await phone.page.reload();
    const refreshed = await appointmentRow(phone, at.date, seeded.vehicle.registration);
    await expect(refreshed.getByRole('button', { name: 'Mark arrived' })).toBeVisible();
    await phone.check('After a reload the next step is offered');

    await front.edge('Both mark it arrived; only the first one counts');
    await frontRow.getByRole('button', { name: 'Mark arrived' }).click();
    await front.expectToast('Marked as arrived');
    await refreshed.getByRole('button', { name: 'Mark arrived' }).click();
    await phone.expectToast(/Cannot .* appointment that is ARRIVED/);
  });

  test('the odometer reading must be valid and never go backwards', async ({ cast }) => {
    const seeded = await seedCustomer(cast, { mileage: 20_000 });
    const at = slot(nextDay(), 10);
    const appointment = await book(seeded.api, seeded.vehicle.id, at);
    const advisorApi = await cast.api('advisor1');
    await advisorApi.post(`/api/v1/appointments/${appointment.id}/confirm`);
    await advisorApi.post(`/api/v1/appointments/${appointment.id}/arrive`);

    const advisor = await cast.actor('Service advisor');
    await advisor.signIn('advisor1');
    await advisor.say('Car on file at 20,000 mi. Opens check-in.');
    const row = await appointmentRow(advisor, at.date, seeded.vehicle.registration);
    await row.getByRole('button', { name: 'Check in' }).click();
    const dialog = advisor.page.getByRole('dialog', { name: 'Check in vehicle' });
    const odometer = dialog.getByLabel('Odometer reading (miles)');
    const submit = dialog.getByRole('button', { name: 'Check in' });

    await advisor.edge('A negative reading');
    await odometer.fill('-5');
    await submit.click();
    await expect(dialog.getByText('Cannot be negative')).toBeVisible();

    await advisor.edge('A decimal reading');
    await odometer.fill('20100.5');
    await submit.click();
    await expect(dialog.getByText('Whole miles only')).toBeVisible();

    await advisor.edge('19,000 mi: lower than the last recorded 20,000');
    await odometer.fill('19000');
    await submit.click();
    await expect(dialog.getByText(/Mileage at check-in \(19000\) is lower than the last recorded reading/)).toBeVisible();
    await advisor.check('Refused by the server; the appointment is still Arrived');

    await advisor.say('Enters the real reading, 20,450 mi');
    await odometer.fill('20450');
    await submit.click();
    await advisor.expectToast(/Checked in/);
    await openJobCardFromRow(advisor, at.date, seeded.vehicle.registration);
    await expect(advisor.page.getByText('20,450 mi at check-in', { exact: false })).toBeVisible();
    await expect(jobCardHeading(advisor)).toContainText('Open');
    await advisor.check('The job card carries the new reading');

    const again = await advisorApi.raw('POST', `/api/v1/appointments/${appointment.id}/check-in`, { mileage: 20_500 });
    expect(again).toMatchObject({ status: 409, code: 'invalid-transition' });
    await cast.note('Checking in the same appointment twice → 409, so no second job card');
  });

  test('status rules: no-show only after the start time, no check-in before arrival', async ({ cast }) => {
    const seeded = await seedCustomer(cast);
    const at = slot(nextDay(), 15);
    const appointment = await book(seeded.api, seeded.vehicle.id, at);
    const advisorApi = await cast.api('advisor1');

    const advisor = await cast.actor('Service advisor');
    await advisor.signIn('advisor1');
    let row = await appointmentRow(advisor, at.date, seeded.vehicle.registration);
    await advisor.check('A future booking offers Confirm, Reschedule and Cancel, but no No-show');
    await expect(row.getByRole('button', { name: 'Confirm' })).toBeVisible();
    await expect(row.getByRole('button', { name: 'No-show' })).toHaveCount(0);

    const noShow = await advisorApi.raw('POST', `/api/v1/appointments/${appointment.id}/no-show`);
    expect(noShow).toMatchObject({ status: 409, code: 'too-early-for-no-show' });
    await cast.note(`No-show before the start time → 409: ${noShow.detail}`);

    const early = await advisorApi.raw('POST', `/api/v1/appointments/${appointment.id}/check-in`, { mileage: 30_000 });
    expect(early).toMatchObject({ status: 409, code: 'invalid-transition' });
    await cast.note(`Check-in before the car arrived → 409: ${early.detail}`);

    const byCustomer = await seeded.api.raw('POST', `/api/v1/appointments/${appointment.id}/confirm`);
    expect(byCustomer.status).toBe(403);
    await cast.note('A customer confirming their own booking → 403 (advisors only)');

    const mechanic = await cast.api('mechanic1');
    const mechanicList = await mechanic.raw('GET', `/api/v1/appointments/${appointment.id}`);
    expect(mechanicList.status).toBe(404);
    await cast.note(`A mechanic reading a booking that is not checked in → ${mechanicList.status}`);

    await advisor.say('Reloads: nothing changed');
    await advisor.page.reload();
    row = await appointmentRow(advisor, at.date, seeded.vehicle.registration);
    await expect(row).toContainText('Booked');
  });
});
