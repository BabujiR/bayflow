import {
  appointmentRow,
  expect,
  jobCardHeading,
  nextDay,
  openJobCardFromRow,
  randomVin,
  slot,
  test,
  uid,
} from './support/bayflow';

/**
 * The whole workflow through the UI, by four people: a new customer signs up and books online,
 * the advisor checks the car in and recommends work, the customer approves part of it, the
 * mechanic does the work, and the customer finds it in the vehicle's service history.
 */
test('a car goes from online booking to service history', async ({ cast }) => {
  const id = uid();
  const username = `e2e-${id.toLowerCase()}`;
  const registration = `E2E ${id}`;
  const at = slot(nextDay(), 10);
  const pads = `Replace front brake pads ${id}`;
  const wipers = `Replace wiper blades ${id}`;

  // ---- Customer signs up, adds a car and books -----------------------------------------------
  const customer = await cast.actor('Customer · new sign-up');
  await customer.say('Creates an account and completes the profile');
  await customer.registerCustomer(username, `Priya ${id}`);

  await customer.say('Adds a car');
  await customer.page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'My vehicles' }).click();
  await customer.page.getByRole('button', { name: 'Add vehicle' }).first().click();
  await customer.page.getByLabel('VIN').fill(randomVin());
  await customer.page.getByLabel('Registration').fill(registration);
  await customer.page.getByLabel('Make').fill('Toyota');
  await customer.page.getByLabel('Model').fill('Corolla');
  await customer.page.getByLabel('Year').fill('2021');
  await customer.page.getByLabel('Mileage (miles)').fill('12000');
  await customer.page.getByRole('button', { name: 'Add vehicle' }).click();
  await expect(customer.page.getByText(registration)).toBeVisible();

  await customer.say('Books a brake check for 10:00');
  await customer.page.goto('/appointments/new');
  await customer.page.getByLabel('Vehicle').selectOption({ label: `${registration} — 2021 Toyota Corolla` });
  await customer.page.getByLabel('Date and time').fill(at.local);
  await customer.page.getByLabel('Service').selectOption('BRAKES');
  await customer.page.getByLabel('Notes for the workshop').fill('Squeal when braking');
  await customer.page.getByRole('button', { name: 'Book appointment' }).click();
  await expect(customer.page).toHaveURL(/\/appointments$/);
  await customer.expectToast(/Booked for/);
  await customer.check('The booking is listed as Booked');

  // ---- Advisor confirms, greets and checks in --------------------------------------------------
  const advisor = await cast.actor('Service advisor · advisor1');
  await advisor.signIn('advisor1');
  await advisor.say("Finds the booking on the day's board");
  await advisor.page.goto('/board');
  await advisor.page.getByLabel('Day').fill(at.date);
  await expect(advisor.page.getByRole('region', { name: 'Booked' })).toContainText(registration);

  await advisor.say('Confirms the booking, then marks the car as arrived');
  let row = await appointmentRow(advisor, at.date, registration);
  await row.getByRole('button', { name: 'Confirm' }).click();
  await advisor.expectToast('Confirmed');
  await row.getByRole('button', { name: 'Mark arrived' }).click();
  await advisor.expectToast('Marked as arrived');

  await advisor.say('Checks the car in with the odometer reading');
  row = await appointmentRow(advisor, at.date, registration);
  await row.getByRole('button', { name: 'Check in' }).click();
  const dialog = advisor.page.getByRole('dialog', { name: 'Check in vehicle' });
  await dialog.getByLabel('Odometer reading (miles)').fill('12480');
  await dialog.getByRole('button', { name: 'Check in' }).click();
  await advisor.expectToast(/Checked in/);

  await advisor.say('Check-in opened a job card in the workshop');
  await openJobCardFromRow(advisor, at.date, registration);
  await expect(advisor.page.getByText('12,480 mi at check-in', { exact: false })).toBeVisible();
  const jobCardUrl = advisor.page.url();

  // ---- Advisor inspects and recommends --------------------------------------------------------
  await advisor.say('Records a finding and recommends two jobs');
  const finding = advisor.page.getByRole('form', { name: 'Add finding' });
  await finding.getByLabel('Finding').fill('Front pads at 2 mm');
  await finding.getByLabel('Severity').selectOption('HIGH');
  await finding.getByRole('button', { name: 'Add finding' }).click();
  await expect(advisor.page.getByText('Front pads at 2 mm')).toBeVisible();

  const recommend = advisor.page.getByRole('form', { name: 'Add recommendation' });
  for (const [description, parts, hours] of [
    [pads, '90.50', '1.25'],
    [wipers, '25', '0.25'],
  ] as const) {
    await recommend.getByLabel('Recommended work').fill(description);
    await recommend.getByLabel('Parts ($)').fill(parts);
    await recommend.getByLabel('Labour (hours)').fill(hours);
    await recommend.getByRole('button', { name: 'Add' }).click();
    await expect(advisor.page.getByText(description)).toBeVisible();
  }
  await advisor.say('Sends the estimate to the customer');
  await advisor.page.getByRole('button', { name: 'Send to customer for approval' }).click();
  await expect(jobCardHeading(advisor)).toContainText('Awaiting approval');

  // ---- Customer approves the pads, declines the wipers -----------------------------------------
  await customer.say('Opens the job card and reviews the estimate');
  await customer.page.goto(jobCardUrl);
  await expect(customer.page.getByText('Please approve or decline each recommended item')).toBeVisible();
  await customer.say('Approves the brake pads, declines the wipers');
  await customer.page.getByRole('button', { name: `Approve ${pads}` }).click();
  await customer.expectToast('Decision recorded');
  await customer.page.getByRole('button', { name: `Decline ${wipers}` }).click();
  await expect(jobCardHeading(customer)).toContainText('Partially approved');
  await expect(customer.page.getByText(`by ${username}`).first()).toBeVisible();
  await customer.check('Each decision is recorded with who made it');

  // ---- Mechanic does the work ----------------------------------------------------------------------
  const mechanic = await cast.actor('Mechanic · mechanic1');
  await mechanic.signIn('mechanic1');
  await mechanic.say('Finds the car under "Ready to start"');
  await mechanic.page.goto('/my-jobs');
  const ready = mechanic.page.getByRole('region', { name: /Ready to start/ });
  await ready.getByRole('link', { name: new RegExp(registration) }).click();
  await mechanic.page.getByRole('button', { name: 'Start work' }).click();
  await expect(jobCardHeading(mechanic)).toContainText('In progress');

  await mechanic.check('Only the approved job can be worked on');
  const work = mechanic.page.getByRole('form', { name: 'Log work' });
  await expect(work.getByLabel('Item').locator('option')).toHaveText(['Choose the approved item', pads]);

  await mechanic.say('Logs the work, the part used and the labour');
  await work.getByLabel('Work done').fill('Fitted new pads and bedded them in');
  await work.getByRole('button', { name: 'Log work' }).click();
  await mechanic.expectToast('Work logged');
  const part = mechanic.page.getByRole('form', { name: 'Add part' });
  await part.getByLabel('Part no.').fill('BP-220');
  await part.getByLabel('Part', { exact: true }).fill('Front brake pad set');
  await part.getByLabel('Qty').fill('1');
  await part.getByLabel('Unit price ($)').fill('84.00');
  await part.getByRole('button', { name: 'Add part' }).click();
  await mechanic.expectToast('Part added');
  const labour = mechanic.page.getByRole('form', { name: 'Add labour' });
  await labour.getByLabel('Hours').fill('1.25');
  await labour.getByRole('button', { name: 'Add labour' }).click();
  await mechanic.expectToast('Labour added');

  await mechanic.say('Completes the job');
  await mechanic.page.getByRole('button', { name: 'Complete job' }).click();
  await expect(jobCardHeading(mechanic)).toContainText('Completed');
  await expect(mechanic.page.getByText('Total', { exact: true })).toBeVisible();
  await mechanic.check('Totals are calculated from the parts and labour actually used');

  // ---- Customer sees it in the history -----------------------------------------------------------
  await customer.say("Checks the car's service history");
  await customer.page.goto('/vehicles');
  await customer.page.getByRole('link', { name: /Toyota Corolla/ }).first().click();
  await expect(customer.page.getByRole('heading', { name: 'Service history' })).toBeVisible();
  await expect(customer.page.getByText(pads)).toBeVisible();
  await expect(customer.page.getByText('Fitted new pads and bedded them in', { exact: false })).toBeVisible();
  await expect(customer.page.getByText('Front brake pad set', { exact: false })).toBeVisible();
  await expect(customer.page.getByText(`Declined: ${wipers}`)).toBeVisible();
  await customer.check('History shows the work, the part, the cost and what was declined');
});
