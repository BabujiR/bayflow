import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu';
import { useId } from 'react';

import { cn } from '@/lib/utils';

export interface MultiSelectOption {
  value: string;
  label: string;
  /** A short line under the label. */
  hint?: string;
  /** Shown ticked and can't be changed (e.g. your own admin role). */
  locked?: boolean;
}

/**
 * A dropdown to pick several options: the closed field lists the chosen ones as chips; the open
 * list has a tick per option and stays open while ticking. Keyboard: Enter/Space opens, arrows
 * move, Space ticks, Escape closes.
 */
export function MultiSelect({
  label,
  options,
  value,
  onChange,
  placeholder = 'Choose…',
  hint,
  error,
}: {
  label: string;
  options: MultiSelectOption[];
  value: string[];
  onChange: (value: string[]) => void;
  placeholder?: string;
  hint?: string;
  error?: string;
}) {
  const id = useId();
  const chosen = options.filter((o) => value.includes(o.value));
  const messageId = `${id}-message`;

  return (
    <div className="grid content-start gap-1.5">
      <span id={`${id}-label`} className="text-sm font-medium leading-none">
        {label}
      </span>
      <DropdownMenuPrimitive.Root>
        <DropdownMenuPrimitive.Trigger
          aria-labelledby={`${id}-label ${id}-value`}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? messageId : undefined}
          className={cn(
            'flex min-h-10 w-full items-center justify-between gap-2 rounded-md border border-input bg-background px-3 py-1.5',
            'text-left text-sm shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring',
            'aria-invalid:border-destructive data-[state=open]:border-primary',
          )}
        >
          <span id={`${id}-value`} className="flex flex-wrap gap-1">
            {chosen.length === 0 ? (
              <span className="text-muted-foreground">{placeholder}</span>
            ) : (
              chosen.map((o) => (
                <span
                  key={o.value}
                  className="rounded-md bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary"
                >
                  {o.label}
                </span>
              ))
            )}
          </span>
          <svg viewBox="0 0 16 16" aria-hidden="true" className="size-4 shrink-0 text-muted-foreground">
            <path
              d="M4 6l4 4 4-4"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.6"
              strokeLinecap="round"
            />
          </svg>
        </DropdownMenuPrimitive.Trigger>
        <DropdownMenuPrimitive.Portal>
          <DropdownMenuPrimitive.Content
            align="start"
            sideOffset={4}
            // Named after the field (Radix would name it after the trigger, i.e. the chosen chips).
            aria-labelledby={`${id}-label`}
            className="z-50 max-h-72 overflow-y-auto rounded-md border bg-card p-1 text-card-foreground shadow-md"
            style={{ width: 'var(--radix-dropdown-menu-trigger-width)' }}
          >
            {options.map((o) => (
              <DropdownMenuPrimitive.CheckboxItem
                key={o.value}
                checked={value.includes(o.value)}
                disabled={o.locked}
                // Stay open while ticking several.
                onSelect={(e) => e.preventDefault()}
                onCheckedChange={(on) =>
                  onChange(on ? [...value, o.value] : value.filter((v) => v !== o.value))
                }
                className={cn(
                  'flex cursor-default select-none items-start gap-2 rounded-sm px-2 py-1.5 text-sm outline-none',
                  'data-[highlighted]:bg-muted data-[disabled]:opacity-60',
                )}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    'mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-sm border',
                    value.includes(o.value)
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-input',
                  )}
                >
                  <DropdownMenuPrimitive.ItemIndicator>
                    <svg viewBox="0 0 16 16" className="size-3">
                      <path
                        d="M3.5 8.5l3 3 6-7"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  </DropdownMenuPrimitive.ItemIndicator>
                </span>
                <span className="grid gap-0.5">
                  <span className="font-medium">{o.label}</span>
                  {o.hint && <span className="text-xs leading-snug text-muted-foreground">{o.hint}</span>}
                </span>
              </DropdownMenuPrimitive.CheckboxItem>
            ))}
          </DropdownMenuPrimitive.Content>
        </DropdownMenuPrimitive.Portal>
      </DropdownMenuPrimitive.Root>
      {error ? (
        <p id={messageId} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p id={messageId} className="text-sm text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
