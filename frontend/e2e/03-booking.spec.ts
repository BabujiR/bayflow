import {
  appointmentRow,
  book,
  expect,
  nextDay,
  randomVin,
  seedCustomer,
  slot,
  test,
  uid,
} from './support/bayflow';

/** Profiles, vehicles and the booking rules: no double booking, reschedule, cancel. */
test.describe('Booking', () => {
  test('a new customer must complete the profile before using the app', async ({ cast }) => {
    const id = uid();
    const username = `e2e-${id.toLowerCase()}`;
    const anonymous = await cast.anonymousApi();
    await anonymous.post('/api/v1/auth/register', {
      data: { username, email: `${username}@example.com`, password: 'E2e-Passw0rd!' },
    });

    const customer = await cast.actor('Customer · no profile yet');
    await customer.page.getByLabel('Username').fill(username);
    await customer.page.getByLabel('Password').fill('E2e-Passw0rd!');
    await customer.page.getByRole('button', { name: 'Sign in' }).click();
    await customer.check('Signed in, but the profile comes first');
    await expect(customer.page.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();
    await customer.edge('Tries to jump straight to booking and to the vehicle list');
    await customer.page.goto('/appointments/new');
    await expect(customer.page.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();
    await customer.page.goto('/vehicles');
    await expect(customer.page.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();

    await customer.edge('Submits the profile without a name');
    await customer.page.getByRole('button', { name: 'Save profile' }).click();
    await expect(customer.page.getByRole('heading', { name: 'Complete your profile' })).toBeVisible();
    await customer.page.getByLabel('Full name').fill(`Sam ${id}`);
    await customer.page.getByRole('button', { name: 'Save profile' }).click();
    await expect(customer.page.getByRole('heading', { name: 'My vehicles' })).toBeVisible();
    await customer.check('With a profile saved, they carry on to the page they asked for');
  });

  test('vehicle rules: VIN format and one VIN per vehicle', async ({ cast }) => {
    const first = await seedCustomer(cast);
    const second = await seedCustomer(cast);
    const customer = await cast.actor('Customer');
    await customer.signIn(second.username, second.password);
    await customer.page.goto('/vehicles');
    await customer.page.getByRole('button', { name: 'Add vehicle' }).first().click();
    await customer.page.getByLabel('Registration').fill(`E2E ${uid()}`);
    await customer.page.getByLabel('Make').fill('Ford');
    await customer.page.getByLabel('Model').fill('Focus');
    await customer.page.getByLabel('Year').fill('2018');
    await customer.page.getByLabel('Mileage (miles)').fill('54000');

    await customer.edge('A VIN with the letter O (never used in VINs)');
    await customer.page.getByLabel('VIN').fill('1HGCM82633AO04352');
    await customer.page.getByRole('button', { name: 'Add vehicle' }).click();
    await expect(customer.page.getByText('17 letters and digits (no I, O or Q)')).toBeVisible();

    await customer.edge('A VIN that is too short');
    await customer.page.getByLabel('VIN').fill('1HGCM8263');
    await customer.page.getByRole('button', { name: 'Add vehicle' }).click();
    await expect(customer.page.getByText('17 letters and digits (no I, O or Q)')).toBeVisible();

    await customer.edge("The VIN of a car already registered by someone else (typed in lower case)");
    await customer.page.getByLabel('VIN').fill(first.vehicle.vin.toLowerCase());
    await customer.page.getByRole('button', { name: 'Add vehicle' }).click();
    await expect(customer.page.getByText('A vehicle with this VIN is already registered.')).toBeVisible();
    await expect(customer.page.locator('[data-sonner-toast]')).toHaveCount(0);
    await customer.check('The error is shown once, next to the VIN (no duplicate toast)');

    await customer.say('A valid, new VIN');
    await customer.page.getByLabel('VIN').fill(randomVin());
    await customer.page.getByRole('button', { name: 'Add vehicle' }).click();
    await expect(customer.page.getByText('Ford Focus').first()).toBeVisible();
    await customer.check('Only the valid car was added');
  });

  test('no double booking for the same car @mobile', async ({ cast }) => {
    const seeded = await seedCustomer(cast);
    const day = nextDay();
    await book(seeded.api, seeded.vehicle.id, slot(day, 10), { durationMinutes: 60 });

    const customer = await cast.actor('Customer');
    await customer.signIn(seeded.username, seeded.password);
    await customer.say('Already booked 10:00–11:00. Tries to book the same car again.');
    await customer.page.goto('/appointments/new');
    await customer.page.getByLabel('Vehicle').selectOption({ label: seeded.vehicle.label });
    const when = customer.page.getByLabel('Date and time');
    const submit = customer.page.getByRole('button', { name: 'Book appointment' });

    await customer.edge('A time in the past');
    await when.fill(slot(-1, 10).local);
    await submit.click();
    await expect(customer.page.getByText('Choose a time in the future')).toBeVisible();

    await customer.edge('10:30, overlapping the existing booking');
    await when.fill(slot(day, 10, 30).local);
    await submit.click();
    await expect(
      customer.page.getByText('This vehicle already has an appointment that overlaps the requested time.'),
    ).toBeVisible();

    await customer.edge('9:30 for 2 hours, which wraps around the existing booking');
    await when.fill(slot(day, 9, 30).local);
    await customer.page.getByLabel('Length').selectOption('120');
    await submit.click();
    await expect(customer.page.getByText(/already has an appointment that overlaps/)).toBeVisible();

    await customer.edge('11:00 for 1 hour: starts exactly when the other ends, so it is allowed');
    await when.fill(slot(day, 11).local);
    await customer.page.getByLabel('Length').selectOption('60');
    await submit.click();
    await customer.expectToast(/Booked for/);
    await expect(customer.page).toHaveURL(/\/appointments$/);
    await customer.check('Back-to-back bookings work; overlapping ones are refused');
  });

  test('reschedule into a clash is refused; a cancelled slot can be booked again', async ({ cast }) => {
    const seeded = await seedCustomer(cast);
    const day = nextDay();
    const morning = slot(day, 9);
    await book(seeded.api, seeded.vehicle.id, morning);
    await book(seeded.api, seeded.vehicle.id, slot(day, 13));

    const customer = await cast.actor('Customer');
    await customer.signIn(seeded.username, seeded.password);
    await customer.page.goto('/appointments');
    await customer.page.getByLabel('Day').fill(morning.date);
    const rows = customer.page.getByRole('row', { name: new RegExp(seeded.vehicle.registration) });
    await expect(rows).toHaveCount(2);

    await customer.edge('Moves the 13:00 booking to 9:30, on top of the 9:00 one');
    const afternoon = rows.filter({ hasText: /1:00/ }).first();
    await afternoon.getByRole('button', { name: 'Reschedule' }).click();
    const dialog = customer.page.getByRole('dialog', { name: 'Reschedule appointment' });
    await dialog.getByLabel('New date and time').fill(slot(day, 9, 30).local);
    await dialog.getByRole('button', { name: 'Reschedule' }).click();
    await expect(dialog.getByText(/overlaps the requested time/)).toBeVisible();

    await customer.say('Moves it to 15:00 instead');
    await dialog.getByLabel('New date and time').fill(slot(day, 15).local);
    await dialog.getByRole('button', { name: 'Reschedule' }).click();
    await customer.expectToast(/Moved to/);

    await customer.say('Cancels the 9:00 booking');
    const nine = rows.filter({ hasText: /9:00/ }).first();
    await nine.getByRole('button', { name: 'Cancel' }).click();
    const confirm = customer.page.getByRole('dialog', { name: 'Cancel this appointment?' });
    await confirm.getByRole('button', { name: 'Cancel appointment' }).click();
    await customer.expectToast('Appointment cancelled');
    const cancelled = rows.filter({ hasText: 'Cancelled' });
    await expect(cancelled).toHaveCount(1);
    await expect(cancelled.getByRole('button')).toHaveCount(0);
    await customer.check('A cancelled booking has no actions left');

    await customer.edge('Books 9:00 again: the cancelled booking no longer holds the slot');
    await customer.page.goto('/appointments/new');
    await customer.page.getByLabel('Vehicle').selectOption({ label: seeded.vehicle.label });
    await customer.page.getByLabel('Date and time').fill(morning.local);
    await customer.page.getByRole('button', { name: 'Book appointment' }).click();
    await customer.expectToast(/Booked for/);

    const advisorApi = await cast.api('advisor1');
    const list = await advisorApi.get<{ content: { id: string; status: string }[] }>(
      `/api/v1/appointments?vehicleId=${seeded.vehicle.id}&status=CANCELLED`,
    );
    const reopen = await advisorApi.raw('POST', `/api/v1/appointments/${list.content[0]!.id}/confirm`);
    expect(reopen).toMatchObject({ status: 409, code: 'invalid-transition' });
    await cast.note(`Advisor confirming the cancelled booking → 409: ${reopen.detail}`);
  });

  test('advisor books for a walk-in customer', async ({ cast }) => {
    const id = uid();
    const name = `Walk-in ${id}`;
    const registration = `E2E ${id}`;
    const at = slot(nextDay(), 14);

    const advisor = await cast.actor('Service advisor · advisor1');
    await advisor.signIn('advisor1');
    await advisor.say('A customer phones in. Creates their profile at the desk.');
    await advisor.page.goto('/customers');
    await advisor.page.getByRole('button', { name: 'New customer' }).click();
    const dialog = advisor.page.getByRole('dialog', { name: 'New customer' });
    await dialog.getByLabel('Full name').fill(name);
    await dialog.getByLabel('Email').fill(`walkin-${id.toLowerCase()}@example.com`);
    await dialog.getByLabel('Phone').fill('+1 555 0100');
    await dialog.getByRole('button', { name: 'Create customer' }).click();

    await advisor.expectToast(`${name} added`);
    await expect(advisor.page.getByRole('heading', { name })).toBeVisible();

    await advisor.say('Adds their car');
    await advisor.page.getByRole('button', { name: 'Add vehicle' }).click();
    await advisor.page.getByLabel('VIN').fill(randomVin());
    await advisor.page.getByLabel('Registration').fill(registration);
    await advisor.page.getByLabel('Make').fill('Mazda');
    await advisor.page.getByLabel('Model').fill('CX-5');
    await advisor.page.getByLabel('Year').fill('2022');
    await advisor.page.getByLabel('Mileage (miles)').fill('8000');
    await advisor.page.getByRole('button', { name: 'Add vehicle' }).click();
    await expect(advisor.page.getByRole('link', { name: new RegExp(registration) })).toBeVisible();

    await advisor.say('Books a service for them');
    await advisor.page.getByRole('link', { name: 'Book a service' }).click();
    await advisor.page.getByLabel('Vehicle').selectOption({ label: `${registration} — 2022 Mazda CX-5` });
    await advisor.page.getByLabel('Date and time').fill(at.local);
    await advisor.page.getByLabel('Service').selectOption('OIL_CHANGE');
    await advisor.page.getByRole('button', { name: 'Book appointment' }).click();
    await advisor.expectToast(/Booked for/);

    const row = await appointmentRow(advisor, at.date, registration);
    await expect(row).toContainText(name);
    await expect(row).toContainText('Booked');
    await advisor.check('The booking is under the walk-in customer, ready for the day');
  });
});
