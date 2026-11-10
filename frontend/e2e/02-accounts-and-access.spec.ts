import {
  checkInViaApi,
  inspectViaApi,
  book,
  expect,
  NEW_PASSWORD,
  nextDay,
  seedCustomer,
  slot,
  test,
  uid,
} from './support/bayflow';

/** Sign-up, sign-in, lockout, sessions, roles and data isolation. */
test.describe('Accounts and access', () => {
  test('sign-up rejects mismatched passwords and a username that is taken', async ({ cast }) => {
    const visitor = await cast.actor('Visitor');
    await visitor.page.goto('/register');

    await visitor.edge('Types two different passwords');
    await visitor.page.getByLabel('Username').fill(`e2e-${uid().toLowerCase()}`);
    await visitor.page.getByLabel('Email').fill(`e2e-${uid().toLowerCase()}@example.com`);
    await visitor.page.getByLabel('Password', { exact: true }).fill(NEW_PASSWORD);
    await visitor.page.getByLabel('Confirm password').fill(`${NEW_PASSWORD}x`);
    await visitor.page.getByRole('button', { name: 'Create account' }).click();
    await expect(visitor.page.getByText('Passwords do not match')).toBeVisible();

    await visitor.edge('Picks a username that already exists (customer1)');
    await visitor.page.getByLabel('Username').fill('customer1');
    await visitor.page.getByLabel('Confirm password').fill(NEW_PASSWORD);
    await visitor.page.getByRole('button', { name: 'Create account' }).click();
    await expect(visitor.page.getByText('That username is already taken.')).toBeVisible();

    await visitor.edge('Uses an email address that already has an account');
    const existing = await seedCustomer(cast);
    await visitor.page.getByLabel('Username').fill(`e2e-${uid().toLowerCase()}`);
    await visitor.page.getByLabel('Email').fill(`${existing.username}@example.com`);
    await visitor.page.getByRole('button', { name: 'Create account' }).click();
    await expect(visitor.page.getByText('An account with that email already exists.')).toBeVisible();
    await visitor.check('No account was created; the form explains each problem in place');
  });

  test('five wrong passwords lock the account, even for the right password', async ({ cast }) => {
    const target = await seedCustomer(cast);
    const intruder = await cast.actor('Someone guessing passwords');
    for (let attempt = 1; attempt <= 5; attempt++) {
      await intruder.edge(`Wrong password, attempt ${attempt} of 5`);
      await intruder.page.getByLabel('Username').fill(target.username);
      await intruder.page.getByLabel('Password').fill(`wrong-${attempt}`);
      await intruder.page.getByRole('button', { name: 'Sign in' }).click();
      await expect(intruder.page.getByText('Username or password is incorrect.')).toBeVisible();
    }
    await intruder.edge('Now the correct password: the account is locked for 15 minutes');
    await intruder.page.getByLabel('Password').fill(target.password);
    await intruder.page.getByRole('button', { name: 'Sign in' }).click();
    await expect(intruder.page.getByText(/Too many failed attempts/)).toBeVisible();
    await expect(intruder.page).toHaveURL(/\/login$/);
    await intruder.check('Locked: the right password does not get in either');
  });

  test('a deep link survives the sign-in, and signing out ends the session @mobile', async ({ cast }) => {
    const customer = await seedCustomer(cast);
    const person = await cast.actor('Customer · signed out');

    await person.edge('Opens a bookmarked page while signed out');
    await person.page.goto('/appointments/new');
    await expect(person.page).toHaveURL(/\/login$/);
    await person.page.getByLabel('Username').fill(customer.username);
    await person.page.getByLabel('Password').fill(customer.password);
    await person.page.getByRole('button', { name: 'Sign in' }).click();
    await expect(person.page.getByRole('heading', { name: 'Book a service' })).toBeVisible();
    await person.check('After signing in they land on the page they asked for');

    await person.say('Reloads the page: the session is restored from the refresh cookie');
    await person.page.reload();
    await expect(person.page.getByRole('heading', { name: 'Book a service' })).toBeVisible();

    await person.say('Signs out');
    await person.signOut();
    await person.edge('Presses Back to the protected page');
    await person.page.goBack();
    await expect(person.page).toHaveURL(/\/login/);
    await person.page.goto('/appointments');
    await expect(person.page).toHaveURL(/\/login/);
    await person.check('Signed out means signed out, including after a reload');
  });

  test('each role only reaches its own pages', async ({ cast }) => {
    const customer = await seedCustomer(cast);
    const forbidden = async (actor: Awaited<ReturnType<typeof cast.actor>>, path: string) => {
      await actor.page.goto(path);
      await expect(actor.page.getByRole('heading', { name: 'Not permitted' })).toBeVisible();
    };

    const c = await cast.actor('Customer');
    await c.signIn(customer.username, customer.password);
    const nav = c.page.getByRole('navigation', { name: 'Main' });
    await expect(nav.getByRole('link', { name: 'My vehicles' })).toBeVisible();
    await expect(nav.getByRole('link', { name: 'Customers' })).toHaveCount(0);
    await c.edge("Types the advisor's board, the customer list and the admin page into the address bar");
    await forbidden(c, '/board');
    await forbidden(c, '/customers');
    await forbidden(c, '/users');
    await c.check('Customers see "Not permitted"');

    const m = await cast.actor('Mechanic');
    await m.signIn('mechanic1');
    await m.edge('Tries the customer list, bookings and user admin');
    await forbidden(m, '/customers');
    await forbidden(m, '/appointments');
    await forbidden(m, '/users');
    await m.check('Mechanics only get My jobs and Job cards');

    const a = await cast.actor('Service advisor');
    await a.signIn('advisor1');
    await a.edge('Tries the admin page');
    await forbidden(a, '/users');

    const admin = await cast.actor('Admin');
    await admin.signIn('admin1');
    await admin.edge('Admin is read-only on workshop pages: no board, no booking');
    await forbidden(admin, '/board');
    await forbidden(admin, '/appointments/new');

    const api = await cast.api(customer.username, customer.password);
    const users = await api.raw('GET', '/api/v1/auth/users');
    expect(users.status).toBe(403);
    await cast.note('Customer token calling GET /auth/users → 403; the API enforces roles, not just the UI');
  });

  test("a customer can't see another customer's job card or car", async ({ cast }) => {
    const owner = await seedCustomer(cast);
    const other = await seedCustomer(cast);
    const advisorApi = await cast.api('advisor1');
    const at = slot(nextDay(), 11);
    const appointment = await book(owner.api, owner.vehicle.id, at);
    const jobCardId = await checkInViaApi(advisorApi, appointment.id, owner.vehicle.mileage + 100);

    const nosy = await cast.actor('Another customer');
    await nosy.signIn(other.username, other.password);
    await nosy.edge("Opens someone else's job card by its address");
    await nosy.page.goto(`/job-cards/${jobCardId}`);
    await expect(nosy.page.getByText('Job card was not found.')).toBeVisible();
    await nosy.edge("Opens someone else's car and its history");
    await nosy.page.goto(`/vehicles/${owner.vehicle.id}`);
    await expect(nosy.page.getByText(/was not found/).first()).toBeVisible();
    await nosy.page.goto('/job-cards');
    await expect(nosy.page.getByText(owner.vehicle.registration)).toHaveCount(0);
    await nosy.check('Other customers\' records look like they do not exist (no 403 that confirms them)');

    await inspectViaApi(advisorApi, jobCardId, []);
    await advisorApi.post(`/api/v1/job-cards/${jobCardId}/complete`);
    type HistoryPage = { content: unknown[] };
    const path = `/api/v1/service-history/vehicles/${owner.vehicle.id}`;
    expect((await owner.api.get<HistoryPage>(path)).content).toHaveLength(1);
    expect((await other.api.get<HistoryPage>(path)).content).toHaveLength(0);
    await cast.note("Service history API: the owner gets their 1 entry, another customer gets an empty list");
  });

  test('disabling an account signs the user out everywhere', async ({ cast }) => {
    const id = uid().toLowerCase();
    const staffName = `e2e-mech-${id}`;
    const admin = await cast.actor('Admin · admin1');
    await admin.signIn('admin1');
    await admin.page.goto('/users');

    await admin.check('Admins cannot disable their own account (no button on their row)');
    const ownRow = admin.page.getByRole('row', { name: /admin1/ });
    await expect(ownRow.getByRole('button')).toHaveCount(0);

    await admin.say('Creates a mechanic account');
    await admin.page.getByRole('button', { name: 'New staff account' }).click();
    const dialog = admin.page.getByRole('dialog', { name: 'New staff account' });
    await admin.edge('First with a username that is taken');
    await dialog.getByLabel('Username').fill('mechanic1');
    await dialog.getByLabel('Email').fill(`${staffName}@example.com`);
    await dialog.getByLabel('Temporary password').fill(NEW_PASSWORD);
    await dialog.getByLabel('Role').selectOption('MECHANIC');
    await dialog.getByRole('button', { name: 'Create account' }).click();
    await expect(dialog.getByText('That username is already taken.')).toBeVisible();
    await dialog.getByLabel('Username').fill(staffName);
    await dialog.getByRole('button', { name: 'Create account' }).click();
    await admin.expectToast(`Account ${staffName} created`);

    const mechanic = await cast.actor('New mechanic');
    await mechanic.signIn(staffName, NEW_PASSWORD);
    await mechanic.page.goto('/my-jobs');
    await expect(mechanic.page.getByRole('heading', { name: 'My jobs' })).toBeVisible();

    await admin.edge('Disables the mechanic while they are signed in');
    await admin.page.getByRole('button', { name: `Disable ${staffName}` }).click();
    await admin.expectToast(`${staffName} disabled`);

    await mechanic.edge('Reloads: the refresh token was revoked');
    await mechanic.page.reload();
    await expect(mechanic.page).toHaveURL(/\/login/);
    await mechanic.page.getByLabel('Username').fill(staffName);
    await mechanic.page.getByLabel('Password').fill(NEW_PASSWORD);
    await mechanic.page.getByRole('button', { name: 'Sign in' }).click();
    await expect(mechanic.page.getByText('This account has been disabled.')).toBeVisible();
    await mechanic.check('Disabled accounts cannot sign back in');

    await admin.say('Enables the account again');
    await admin.page.getByRole('button', { name: `Enable ${staffName}` }).click();
    await admin.expectToast(`${staffName} enabled`);
    await mechanic.signIn(staffName, NEW_PASSWORD);
    await mechanic.check('Re-enabled: signing in works again');
  });
});
