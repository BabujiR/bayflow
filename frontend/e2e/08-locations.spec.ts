import {
  checkInViaApi,
  expect,
  NEW_PASSWORD,
  nextDay,
  seedCustomer,
  slot,
  test,
  uid,
  type Api,
} from './support/bayflow';

/**
 * Organizations and locations (phases L1–L3): a workshop opens a second location on the other
 * side of the world, staffs it, and serves a customer there. Each location keeps its own staff,
 * board, time zone and labour rate. The scenario archives the location again at the end, so the
 * other scenarios keep running against one location.
 */
test.describe('Locations', () => {
  test('a second location with its own staff, time zone and labour rate', async ({ cast }) => {
    const id = uid().toLowerCase();
    const name = `North ${id}`;
    const code = `n-${id}`;
    const advisorName = `e2e-north-${id}`;
    const adminApi = await cast.api('admin1');
    let locationId: string | undefined;
    let appointmentId: string | undefined;
    let customerApi: Api | undefined;

    try {
      // ---- The owner adds the location and staffs it ----------------------------------------
      const admin = await cast.actor('Owner · admin1');
      await admin.signIn('admin1');
      await admin.say('Opens a location in Auckland, with its own labour rate');
      await admin.page.goto('/locations');
      await admin.page.getByRole('button', { name: 'Add location' }).click();
      const form = admin.page.getByRole('form', { name: 'Add location' });
      await form.getByLabel('Name').fill(name);
      await form.getByLabel('Code').fill(code);
      await form.getByLabel('Time zone').selectOption('Pacific/Auckland');
      await form.getByLabel('Labour rate per hour').fill('140');
      await form.getByRole('button', { name: 'Add location' }).click();
      await admin.expectToast(`${name} saved`);
      const row = admin.page.getByRole('row', { name: new RegExp(name) });
      await expect(row).toContainText('Pacific/Auckland');
      const locations = await adminApi.get<{ id: string; code: string }[]>('/api/v1/auth/locations');
      locationId = locations.find((l) => l.code === code)?.id;
      expect(locationId, 'the new location').toBeTruthy();

      await admin.say('Hires an advisor who works only there');
      await admin.page.goto('/users');
      await admin.page.getByRole('button', { name: 'New staff account' }).click();
      const dialog = admin.page.getByRole('dialog', { name: 'New staff account' });
      await dialog.getByLabel('Username').fill(advisorName);
      await dialog.getByLabel('Email').fill(`${advisorName}@example.com`);
      await dialog.getByLabel('Temporary password').fill(NEW_PASSWORD);
      await dialog.getByLabel('Role').selectOption('SERVICE_ADVISOR');
      await dialog.getByRole('checkbox', { name: new RegExp(name) }).check();
      await dialog.getByRole('button', { name: 'Create account' }).click();
      await admin.expectToast(`Account ${advisorName} created`);

      // ---- A customer books at the new location, in its local time ---------------------------
      const seeded = await seedCustomer(cast);
      customerApi = seeded.api;
      const day = slot(nextDay(), 10).date;
      const customer = await cast.actor('Customer');
      await customer.signIn(seeded.username, seeded.password);
      await customer.say(`Books at ${name} for 10:00 Auckland time`);
      await customer.page.goto('/appointments/new');
      await customer.page.getByLabel('Location').selectOption({ label: name });
      await expect(customer.page.getByText(/times in Pacific\/Auckland/)).toBeVisible();
      await customer.page.getByLabel('Vehicle').selectOption(seeded.vehicle.id);
      await customer.page.getByLabel('Date and time').fill(`${day}T10:00`);
      await customer.page.getByRole('button', { name: 'Book appointment' }).click();
      await customer.expectToast(/Booked for .*10:00/);
      const mine = await seeded.api.get<{ content: { id: string; locationId: string }[] }>(
        `/api/v1/appointments?vehicleId=${seeded.vehicle.id}`,
      );
      appointmentId = mine.content.find((a) => a.locationId === locationId)?.id;
      expect(appointmentId, 'booked at the new location').toBeTruthy();

      // ---- Each location's staff see their own --------------------------------------------
      const north = await cast.actor(`Advisor · ${name}`);
      await north.signIn(advisorName, NEW_PASSWORD);
      await north.say("Opens the board for the booking's day: it is on their board");
      await north.page.goto('/board');
      await north.page.getByLabel('Day').fill(day);
      await expect(north.page.getByText(seeded.vehicle.registration).first()).toBeVisible();
      await expect(north.page.getByText('10:00').first()).toBeVisible();

      const mainAdvisor = await cast.api('advisor1');
      const fromMain = await mainAdvisor.raw('GET', `/api/v1/appointments/${appointmentId}`);
      expect(fromMain.status).toBe(404);
      await cast.note("advisor1 works at Main: North's booking is not found (404)");

      // ---- The job card freezes the new location's rate -------------------------------------
      const northApi = await cast.api(advisorName, NEW_PASSWORD);
      const jobCardId = await checkInViaApi(northApi, appointmentId!, seeded.vehicle.mileage + 100);
      const card = await northApi.get<{ labourRate: number; locationId: string }>(`/api/v1/job-cards/${jobCardId}`);
      expect(card.locationId).toBe(locationId);
      expect(card.labourRate).toBe(140);
      await cast.note(`The job card belongs to ${name} and charges its 140/h`);

      // ---- The owner switches between locations ------------------------------------------
      await admin.say('Looks at one location at a time with the switcher');
      await admin.page.goto('/appointments');
      const switcher = admin.page.getByRole('combobox', { name: 'Location' });
      await switcher.selectOption({ label: name });
      await admin.page.getByLabel('Day').fill(day);
      await expect(admin.page.getByRole('row', { name: new RegExp(seeded.vehicle.registration) })).toBeVisible();
      await switcher.selectOption({ label: 'Main' });
      await expect(admin.page.getByRole('row', { name: new RegExp(seeded.vehicle.registration) })).toHaveCount(0);
      await switcher.selectOption({ label: 'All locations' });
      await expect(admin.page.getByRole('columnheader', { name: 'Location' })).toBeVisible();
      await admin.check('One location, the other, or all of them, with each row showing where it is');
    } finally {
      // Back to one location, so every other scenario starts as before.
      // A booking that never got checked in would block archiving: its customer cancels it.
      if (appointmentId && customerApi) {
        await customerApi.raw('POST', `/api/v1/appointments/${appointmentId}/cancel`);
      }
      if (locationId) {
        const archived = await adminApi.raw('POST', `/api/v1/auth/locations/${locationId}/archive`);
        expect(archived.status, 'the scenario archives its location').toBe(200);
      }
    }
  });
});
