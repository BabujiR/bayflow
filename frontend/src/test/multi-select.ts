import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * Ticks (or unticks) one option of a MultiSelect field: opens its dropdown (the field's button,
 * named after its label), clicks the option, closes the dropdown.
 */
export async function toggleOption(container: HTMLElement, field: string, option: RegExp | string) {
  await userEvent.click(within(container).getByRole('button', { name: new RegExp(`^${field}`) }));
  const menu = await screen.findByRole('menu', { name: field });
  await userEvent.click(within(menu).getByRole('menuitemcheckbox', { name: option }));
  await userEvent.keyboard('{Escape}');
}

/** The option as the open dropdown shows it (opens the field first). */
export async function optionOf(container: HTMLElement, field: string, option: RegExp | string) {
  await userEvent.click(within(container).getByRole('button', { name: new RegExp(`^${field}`) }));
  const menu = await screen.findByRole('menu', { name: field });
  return within(menu).getByRole('menuitemcheckbox', { name: option });
}
