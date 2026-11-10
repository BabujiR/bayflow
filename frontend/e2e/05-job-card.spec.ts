import {
  book,
  checkInViaApi,
  expect,
  inspectViaApi,
  jobCardHeading,
  nextDay,
  seedCustomer,
  slot,
  test,
  uid,
  type Cast,
  type SeededCustomer,
} from './support/bayflow';

/** A checked-in car with an open job card, arranged through the API. */
async function openJobCard(cast: Cast) {
  const customer = await seedCustomer(cast);
  const advisorApi = await cast.api('advisor1');
  const appointment = await book(customer.api, customer.vehicle.id, slot(nextDay(), 10));
  const jobCardId = await checkInViaApi(advisorApi, appointment.id, customer.vehicle.mileage + 250);
  return { customer, advisorApi, jobCardId };
}

async function signInCustomer(cast: Cast, customer: SeededCustomer, name = 'Customer') {
  const actor = await cast.actor(name);
  await actor.signIn(customer.username, customer.password);
  return actor;
}

/** Inspection, approval, work and completion: the job card state machine from every side. */
test.describe('Job card', () => {
  test('customer declines everything: the job closes as inspection only', async ({ cast }) => {
    const { customer, advisorApi, jobCardId } = await openJobCard(cast);
    const id = uid();
    const items = [
      { description: `Replace timing belt ${id}`, parts: 310, hours: 3 },
      { description: `Flush coolant ${id}`, parts: 40, hours: 0.5 },
    ];
    await inspectViaApi(advisorApi, jobCardId, items);

    const c = await signInCustomer(cast, customer);
    await c.page.goto(`/job-cards/${jobCardId}`);
    await c.say('Reviews a $600+ estimate and decides against all of it');
    for (const item of items) {
      await c.page.getByRole('button', { name: `Decline ${item.description}` }).click();
      await c.expectToast('Decision recorded');
    }
    await expect(jobCardHeading(c)).toContainText('Declined');

    const mechanic = await cast.actor('Mechanic');
    await mechanic.signIn('mechanic1');
    await mechanic.page.goto(`/job-cards/${jobCardId}`);
    await mechanic.edge('A declined job cannot be started');
    await expect(mechanic.page.getByRole('button', { name: 'Start work' })).toHaveCount(0);
    const start = await (await cast.api('mechanic1')).raw('POST', `/api/v1/job-cards/${jobCardId}/start`);
    expect(start).toMatchObject({ status: 409, code: 'invalid-transition' });
    await cast.note(`Starting a declined job via the API → 409: ${start.detail}`);

    const advisor = await cast.actor('Service advisor');
    await advisor.signIn('advisor1');
    await advisor.page.goto(`/job-cards/${jobCardId}`);
    await advisor.say('Closes the job: the car goes home without work');
    await advisor.page.getByRole('button', { name: 'Complete job' }).click();
    await expect(jobCardHeading(advisor)).toContainText('Completed');
    await expect(advisor.page.getByText('Inspection only: no work was approved.')).toBeVisible();

    await c.say("Checks the car's history");
    await c.page.goto(`/vehicles/${customer.vehicle.id}`);
    await expect(c.page.getByText('Inspection only — no work was carried out.')).toBeVisible();
    await expect(c.page.getByText(`Declined: ${items.map((i) => i.description).join(', ')}`)).toBeVisible();
    await c.check('The visit is recorded with what was declined and no charge for work');
  });

  test('nothing to fix: the advisor finishes the inspection without recommending work', async ({ cast }) => {
    const { advisorApi, jobCardId } = await openJobCard(cast);
    const advisor = await cast.actor('Service advisor');
    await advisor.signIn('advisor1');
    await advisor.page.goto(`/job-cards/${jobCardId}`);

    await advisor.edge('Tries to add a finding with no description');
    const finding = advisor.page.getByRole('form', { name: 'Add finding' });
    await finding.getByRole('button', { name: 'Add finding' }).click();
    await expect(finding.getByText('Describe what you found')).toBeVisible();

    await advisor.say('Records that all is well');
    await finding.getByLabel('Finding').fill('Brakes, tyres and fluids all within spec');
    await finding.getByLabel('Severity').selectOption('LOW');
    await finding.getByRole('button', { name: 'Add finding' }).click();
    await expect(jobCardHeading(advisor)).toContainText('Inspected');

    await advisor.edge('No recommendations: the button offers to finish the inspection instead');
    await advisor.page.getByRole('button', { name: 'Finish inspection (no work needed)' }).click();
    await expect(jobCardHeading(advisor)).toContainText('Declined');
    await expect(advisor.page.getByRole('form', { name: 'Add finding' })).toHaveCount(0);
    await advisor.check('Findings are locked once the inspection is finished');
    await advisor.page.getByRole('button', { name: 'Complete job' }).click();
    await expect(jobCardHeading(advisor)).toContainText('Completed');

    const late = await advisorApi.raw('POST', `/api/v1/job-cards/${jobCardId}/findings`, {
      description: 'late',
      severity: 'LOW',
    });
    expect(late).toMatchObject({ status: 409, code: 'invalid-transition' });
    await cast.note(`Adding a finding to a completed job card → 409: ${late.detail}`);
  });

  test('customer and advisor decide the same item at the same moment', async ({ cast }) => {
    const { customer, advisorApi, jobCardId } = await openJobCard(cast);
    const id = uid();
    const oil = `Oil and filter change ${id}`;
    const tyres = `Rotate tyres ${id}`;
    await inspectViaApi(advisorApi, jobCardId, [
      { description: oil, parts: 45, hours: 0.5 },
      { description: tyres, parts: 0, hours: 0.75 },
    ]);

    const c = await signInCustomer(cast, customer);
    await c.page.goto(`/job-cards/${jobCardId}`);
    const advisor = await cast.actor('Service advisor (customer on the phone)');
    await advisor.signIn('advisor1');
    await advisor.page.goto(`/job-cards/${jobCardId}`);
    await advisor.say('Both have the estimate open');

    await c.say(`Approves "${oil}" in the app`);
    await c.page.getByRole('button', { name: `Approve ${oil}` }).click();
    await c.expectToast('Decision recorded');

    await advisor.edge('Meanwhile records "decline" for the same item on a stale screen');
    await advisor.page.getByRole('button', { name: `Decline ${oil}` }).click();
    await advisor.expectToast('This recommendation was already approved.');
    await advisor.check("The customer's decision stands; the advisor's is refused");

    await advisor.say('Reloads and records the second item on the customer’s behalf');
    await advisor.page.reload();
    await expect(advisor.page.getByText(`by ${customer.username}`)).toBeVisible();
    await advisor.page.getByRole('button', { name: `Approve ${tyres}` }).click();
    await expect(jobCardHeading(advisor)).toContainText('Approved');
    await expect(advisor.page.getByText('by advisor1')).toBeVisible();
    await advisor.check('Each decision shows who made it: the customer or the advisor');

    await c.page.reload();
    await expect(jobCardHeading(c)).toContainText('Approved');
    await expect(c.page.getByRole('button', { name: /^Approve / })).toHaveCount(0);
  });

  test('work rules: only approved items, every one logged before completing', async ({ cast }) => {
    const { customer, advisorApi, jobCardId } = await openJobCard(cast);
    const id = uid();
    const battery = `Replace battery ${id}`;
    const bulb = `Replace headlight bulb ${id}`;
    const wipers = `Replace wipers ${id}`;
    const items = await inspectViaApi(advisorApi, jobCardId, [
      { description: battery, parts: 140, hours: 0.5 },
      { description: bulb, parts: 18, hours: 0.25 },
      { description: wipers, parts: 25, hours: 0.25 },
    ]);
    const byName = (d: string) => items.find((i) => i.description === d)!.id;
    await customer.api.post(`/api/v1/job-cards/${jobCardId}/recommendations/${byName(battery)}/approve`);
    await customer.api.post(`/api/v1/job-cards/${jobCardId}/recommendations/${byName(bulb)}/approve`);
    await customer.api.post(`/api/v1/job-cards/${jobCardId}/recommendations/${byName(wipers)}/decline`);

    const m = await cast.actor('Mechanic');
    await m.signIn('mechanic1');
    await m.page.goto(`/job-cards/${jobCardId}`);
    await expect(jobCardHeading(m)).toContainText('Partially approved');
    await m.say('Two of three items approved. Starts work.');
    await m.page.getByRole('button', { name: 'Start work' }).click();
    await expect(jobCardHeading(m)).toContainText('In progress');

    const work = m.page.getByRole('form', { name: 'Log work' });
    await m.check('The item list holds only the approved items');
    await expect(work.getByLabel('Item').locator('option')).toHaveText(['Choose the approved item', battery, bulb]);

    const mechanicApi = await cast.api('mechanic1');
    const declined = await mechanicApi.raw('POST', `/api/v1/job-cards/${jobCardId}/work-logs`, {
      recommendationId: byName(wipers),
      description: 'Fitted wipers anyway',
    });
    expect(declined.code).toBe('item-not-approved');
    await cast.note(`Logging work on the declined item via the API → ${declined.status}: ${declined.detail}`);

    await m.edge('Logs work without choosing the item');
    await work.getByLabel('Work done').fill('Fitted new battery');
    await work.getByRole('button', { name: 'Log work' }).click();
    await expect(work.getByText('Choose the item')).toBeVisible();
    await work.getByLabel('Item').selectOption({ label: battery });
    await work.getByRole('button', { name: 'Log work' }).click();
    await m.expectToast('Work logged');

    await m.edge('Labour of 30 hours and a part with quantity 0');
    const labour = m.page.getByRole('form', { name: 'Add labour' });
    await labour.getByLabel('Item').selectOption({ label: battery });
    await labour.getByLabel('Hours').fill('30');
    await labour.getByRole('button', { name: 'Add labour' }).click();
    await expect(labour.getByText('At most 24 hours')).toBeVisible();
    const part = m.page.getByRole('form', { name: 'Add part' });
    await part.getByLabel('Item').selectOption({ label: battery });
    await part.getByLabel('Part no.').fill('BAT-70');
    await part.getByLabel('Part', { exact: true }).fill('70Ah battery');
    await part.getByLabel('Qty').fill('0');
    await part.getByLabel('Unit price ($)').fill('139.99');
    await part.getByRole('button', { name: 'Add part' }).click();
    await expect(part.getByText('At least 1')).toBeVisible();
    await m.say('Corrects both');
    await labour.getByLabel('Hours').fill('0.5');
    await labour.getByRole('button', { name: 'Add labour' }).click();
    await m.expectToast('Labour added');
    await part.getByLabel('Qty').fill('1');
    await part.getByRole('button', { name: 'Add part' }).click();
    await m.expectToast('Part added');

    await m.edge('Tries to complete with the headlight bulb not logged');
    await m.page.getByRole('button', { name: 'Complete job' }).click();
    await m.expectToast(new RegExp(`Log the work done for every approved item before completing\\. Missing: ${bulb}`));
    await expect(jobCardHeading(m)).toContainText('In progress');

    await m.say('Logs the bulb, then completes');
    await work.getByLabel('Item').selectOption({ label: bulb });
    await work.getByLabel('Work done').fill('Replaced left low-beam bulb');
    await work.getByRole('button', { name: 'Log work' }).click();
    await m.expectToast('Work logged');
    await m.page.getByRole('button', { name: 'Complete job' }).click();
    await expect(jobCardHeading(m)).toContainText('Completed');
    await expect(m.page.getByRole('form', { name: 'Log work' })).toHaveCount(0);
    await m.check('Completed: totals are fixed and the work forms are gone');

    const after = await mechanicApi.raw('POST', `/api/v1/job-cards/${jobCardId}/labour`, {
      recommendationId: byName(battery),
      hours: 1,
    });
    expect(after.status).toBe(409);
    await cast.note('Adding labour to a completed job card → 409; history is immutable');
  });

  test('who can do what on a job card', async ({ cast }) => {
    const { customer, jobCardId } = await openJobCard(cast);

    const m = await cast.actor('Mechanic');
    await m.signIn('mechanic1');
    await m.page.goto(`/job-cards/${jobCardId}`);
    await m.check('Mechanics record findings but do not price work');
    await expect(m.page.getByRole('form', { name: 'Add finding' })).toBeVisible();
    await expect(m.page.getByRole('form', { name: 'Add recommendation' })).toHaveCount(0);
    const finding = m.page.getByRole('form', { name: 'Add finding' });
    await finding.getByLabel('Finding').fill('Rear shock absorber leaking');
    await finding.getByLabel('Severity').selectOption('CRITICAL');
    await finding.getByRole('button', { name: 'Add finding' }).click();
    await m.expectToast('Finding recorded');
    await expect(m.page.getByRole('button', { name: 'Send to customer for approval' })).toHaveCount(0);

    const mechanicApi = await cast.api('mechanic1');
    const recommend = await mechanicApi.raw('POST', `/api/v1/job-cards/${jobCardId}/recommendations`, {
      description: 'Replace shocks',
      estimatedPartsCost: 200,
      estimatedLabourHours: 2,
    });
    expect(recommend.status).toBe(403);
    await cast.note('Mechanic adding a priced recommendation via the API → 403');

    const c = await signInCustomer(cast, customer);
    await c.page.goto(`/job-cards/${jobCardId}`);
    await c.check('The customer sees the findings, but no forms and no buttons to change them');
    await expect(c.page.getByText('Rear shock absorber leaking')).toBeVisible();
    await expect(c.page.getByRole('form')).toHaveCount(0);
    await expect(c.page.getByRole('button', { name: 'Complete job' })).toHaveCount(0);

    const decideEarly = await customer.api.raw('POST', `/api/v1/job-cards/${jobCardId}/complete`);
    expect(decideEarly.status).toBe(403);
    await cast.note('Customer completing the job via the API → 403');

    const admin = await cast.actor('Admin');
    await admin.signIn('admin1');
    await admin.page.goto(`/job-cards/${jobCardId}`);
    await admin.check('Admins can look but not touch');
    await expect(admin.page.getByText('Rear shock absorber leaking')).toBeVisible();
    await expect(admin.page.getByRole('form')).toHaveCount(0);
  });
});
