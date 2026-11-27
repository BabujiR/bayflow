import { expect, jobCardHeading, seedCustomer, test, uid } from './support/bayflow';

/** A customer phones for a price: an estimate (and its job card) before any visit is booked. */
test.describe('Quick estimate', () => {
  test('advisor prices a phone enquiry, then closes it when the customer passes', async ({ cast }) => {
    const customer = await seedCustomer(cast);
    const request = `Brakes squeal when stopping; quote front pads ${uid()}`;

    const advisor = await cast.actor('Service advisor');
    await advisor.signIn('advisor1');
    await advisor.page.goto('/job-cards');
    await advisor.say('A customer phones asking what front brakes would cost');
    await advisor.page.getByRole('link', { name: 'New estimate' }).click();
    await advisor.page.getByLabel('Customer').fill(customer.name);
    await advisor.page.getByRole('button', { name: new RegExp(customer.name) }).click();
    await advisor.page.getByLabel('Vehicle').selectOption(customer.vehicle.id);
    await advisor.page.getByLabel('What the customer is asking for').fill(request);
    await advisor.page.getByRole('button', { name: 'Start estimate' }).click();

    await expect(jobCardHeading(advisor)).toContainText(customer.vehicle.registration);
    await expect(
      advisor.page.getByRole('region', { name: 'Estimate' }).getByText('Estimate · not scheduled yet'),
    ).toBeVisible();
    await expect(advisor.page.getByText(`Customer's request: ${request}`)).toBeVisible();
    const jobCardUrl = advisor.page.url();

    await advisor.page.goto('/job-cards');
    await advisor.page.getByLabel('Show').selectOption('estimates');
    await expect(advisor.page.getByRole('link', { name: customer.vehicle.registration })).toBeVisible();

    // The mechanics' list only has cars at the shop.
    const mechanicApi = await cast.api('mechanic1');
    const jobCardId = jobCardUrl.split('/').pop()!;
    const start = await mechanicApi.raw('POST', `/api/v1/job-cards/${jobCardId}/start`);
    expect(start.status).toBe(409);

    await advisor.page.goto(jobCardUrl);
    await advisor.say('The customer decides not to go ahead');
    await advisor.page.getByRole('button', { name: 'Close estimate' }).click();
    await advisor.expectToast('Estimate closed');
    await expect(
      advisor.page.getByRole('region', { name: 'Estimate' }).getByText('Estimate closed', { exact: true }),
    ).toBeVisible();
    await expect(jobCardHeading(advisor)).toContainText('Declined');
  });
});
