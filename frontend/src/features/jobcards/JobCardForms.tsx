import { zodResolver } from '@hookform/resolvers/zod';
import type { ComponentProps } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import type { Recommendation, Severity } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { SEVERITY_LABELS } from '@/lib/format';

const SEVERITIES = Object.keys(SEVERITY_LABELS) as [Severity, ...Severity[]];
const money = z.coerce.number<string>().min(0, 'Cannot be negative').multipleOf(0.01, 'Use whole cents');

/** All forms here reset after a successful submit; errors are toasted by the caller. */
type Submit<T> = (values: T) => Promise<unknown>;

const findingSchema = z.object({
  description: z.string().trim().min(1, 'Describe what you found').max(1000),
  severity: z.enum(SEVERITIES),
});

export function FindingForm({ onSubmit }: { onSubmit: Submit<z.output<typeof findingSchema>> }) {
  const { register, handleSubmit, reset, formState } = useForm<z.input<typeof findingSchema>, unknown, z.output<typeof findingSchema>>({
    resolver: zodResolver(findingSchema),
    defaultValues: { description: '', severity: 'MEDIUM' },
  });
  return (
    <form
      noValidate
      aria-label="Add finding"
      className="grid gap-3 sm:grid-cols-[1fr_10rem_auto] sm:items-start"
      onSubmit={handleSubmit(async (values) => {
        await onSubmit(values).then(() => reset()).catch(() => undefined);
      })}
    >
      <Field label="Finding" error={formState.errors.description?.message}>
        <Input placeholder="e.g. Front brake pads at 2 mm" {...register('description')} />
      </Field>
      <Field label="Severity">
        <Select {...register('severity')}>
          {SEVERITIES.map((s) => (
            <option key={s} value={s}>
              {SEVERITY_LABELS[s]}
            </option>
          ))}
        </Select>
      </Field>
      <Button type="submit" className="sm:mt-5" disabled={formState.isSubmitting}>
        Add finding
      </Button>
    </form>
  );
}

const recommendationSchema = z.object({
  description: z.string().trim().min(1, 'Describe the work').max(1000),
  estimatedPartsCost: money,
  estimatedLabourHours: z.coerce.number<string>().min(0, 'Cannot be negative').max(999.99, 'Too many hours'),
});

export function RecommendationForm({
  labourRate,
  onSubmit,
}: {
  labourRate: number;
  onSubmit: Submit<z.output<typeof recommendationSchema>>;
}) {
  const { register, handleSubmit, reset, formState } = useForm<
    z.input<typeof recommendationSchema>,
    unknown,
    z.output<typeof recommendationSchema>
  >({
    resolver: zodResolver(recommendationSchema),
    defaultValues: { description: '', estimatedPartsCost: '', estimatedLabourHours: '' },
  });
  return (
    <form
      noValidate
      aria-label="Add recommendation"
      className="grid gap-3 sm:grid-cols-[1fr_9rem_9rem_auto] sm:items-start"
      onSubmit={handleSubmit(async (values) => {
        await onSubmit(values).then(() => reset()).catch(() => undefined);
      })}
    >
      <Field label="Recommended work" error={formState.errors.description?.message}>
        <Input placeholder="e.g. Replace front brake pads" {...register('description')} />
      </Field>
      <Field label="Parts ($)" error={formState.errors.estimatedPartsCost?.message}>
        <Input inputMode="decimal" {...register('estimatedPartsCost')} />
      </Field>
      <Field label="Labour (hours)" error={formState.errors.estimatedLabourHours?.message} hint={`at $${labourRate}/h`}>
        <Input inputMode="decimal" {...register('estimatedLabourHours')} />
      </Field>
      <Button type="submit" className="sm:mt-5" disabled={formState.isSubmitting}>
        Add
      </Button>
    </form>
  );
}

const workSchema = z.object({
  recommendationId: z.string().min(1, 'Choose the item'),
  description: z.string().trim().min(1, 'Describe the work done').max(1000),
});

const partSchema = z.object({
  recommendationId: z.string().min(1, 'Choose the item'),
  partNumber: z.string().trim().min(1, 'Enter the part number').max(50),
  name: z.string().trim().min(1, 'Enter the part name').max(200),
  quantity: z.coerce.number<string>().int('Whole numbers only').min(1, 'At least 1').max(1000),
  unitPrice: money,
});

const labourSchema = z.object({
  recommendationId: z.string().min(1, 'Choose the item'),
  hours: z.coerce.number<string>().min(0.01, 'More than zero').max(24, 'At most 24 hours'),
  hourlyRate: z.string().refine((v) => v === '' || (!Number.isNaN(Number(v)) && Number(v) >= 0), 'Enter a rate'),
});

function ItemSelect({ items, ...props }: { items: Recommendation[] } & ComponentProps<'select'>) {
  return (
    <Select {...props}>
      <option value="">Choose the approved item</option>
      {items.map((item) => (
        <option key={item.id} value={item.id}>
          {item.description}
        </option>
      ))}
    </Select>
  );
}

/** A mechanic records work, parts and labour against an approved recommendation. */
export function WorkForms({
  items,
  labourRate,
  onWork,
  onPart,
  onLabour,
}: {
  items: Recommendation[];
  labourRate: number;
  onWork: Submit<z.output<typeof workSchema>>;
  onPart: Submit<z.output<typeof partSchema>>;
  onLabour: Submit<{ recommendationId: string; hours: number; hourlyRate?: number }>;
}) {
  const work = useForm<z.input<typeof workSchema>, unknown, z.output<typeof workSchema>>({
    resolver: zodResolver(workSchema),
    defaultValues: { recommendationId: items.length === 1 ? items[0]!.id : '', description: '' },
  });
  const part = useForm<z.input<typeof partSchema>, unknown, z.output<typeof partSchema>>({
    resolver: zodResolver(partSchema),
    defaultValues: {
      recommendationId: items.length === 1 ? items[0]!.id : '',
      partNumber: '',
      name: '',
      quantity: '1',
      unitPrice: '',
    },
  });
  const labour = useForm<z.input<typeof labourSchema>, unknown, z.output<typeof labourSchema>>({
    resolver: zodResolver(labourSchema),
    defaultValues: { recommendationId: items.length === 1 ? items[0]!.id : '', hours: '', hourlyRate: '' },
  });

  return (
    <div className="grid gap-6">
      <form
        noValidate
        aria-label="Log work"
        className="grid gap-3 sm:grid-cols-[14rem_1fr_auto] sm:items-start"
        onSubmit={work.handleSubmit(async (values) => {
          await onWork(values)
            .then(() => work.reset({ recommendationId: values.recommendationId, description: '' }))
            .catch(() => undefined);
        })}
      >
        <Field label="Item" error={work.formState.errors.recommendationId?.message}>
          <ItemSelect items={items} {...work.register('recommendationId')} />
        </Field>
        <Field label="Work done" error={work.formState.errors.description?.message}>
          <Input placeholder="e.g. Fitted new pads and bedded them in" {...work.register('description')} />
        </Field>
        <Button type="submit" className="sm:mt-5" disabled={work.formState.isSubmitting}>
          Log work
        </Button>
      </form>

      <form
        noValidate
        aria-label="Add part"
        className="grid gap-3 sm:grid-cols-[14rem_8rem_1fr_5rem_7rem_auto] sm:items-start"
        onSubmit={part.handleSubmit(async (values) => {
          await onPart(values)
            .then(() =>
              part.reset({ recommendationId: values.recommendationId, partNumber: '', name: '', quantity: '1', unitPrice: '' }),
            )
            .catch(() => undefined);
        })}
      >
        <Field label="Item" error={part.formState.errors.recommendationId?.message}>
          <ItemSelect items={items} {...part.register('recommendationId')} />
        </Field>
        <Field label="Part no." error={part.formState.errors.partNumber?.message}>
          <Input {...part.register('partNumber')} />
        </Field>
        <Field label="Part" error={part.formState.errors.name?.message}>
          <Input {...part.register('name')} />
        </Field>
        <Field label="Qty" error={part.formState.errors.quantity?.message}>
          <Input inputMode="numeric" {...part.register('quantity')} />
        </Field>
        <Field label="Unit price ($)" error={part.formState.errors.unitPrice?.message}>
          <Input inputMode="decimal" {...part.register('unitPrice')} />
        </Field>
        <Button type="submit" className="sm:mt-5" disabled={part.formState.isSubmitting}>
          Add part
        </Button>
      </form>

      <form
        noValidate
        aria-label="Add labour"
        className="grid gap-3 sm:grid-cols-[14rem_7rem_9rem_auto] sm:items-start"
        onSubmit={labour.handleSubmit(async ({ recommendationId, hours, hourlyRate }) => {
          await onLabour({ recommendationId, hours, hourlyRate: hourlyRate === '' ? undefined : Number(hourlyRate) })
            .then(() => labour.reset({ recommendationId, hours: '', hourlyRate: '' }))
            .catch(() => undefined);
        })}
      >
        <Field label="Item" error={labour.formState.errors.recommendationId?.message}>
          <ItemSelect items={items} {...labour.register('recommendationId')} />
        </Field>
        <Field label="Hours" error={labour.formState.errors.hours?.message}>
          <Input inputMode="decimal" {...labour.register('hours')} />
        </Field>
        <Field label="Rate ($/h)" error={labour.formState.errors.hourlyRate?.message} hint={`Default $${labourRate}`}>
          <Input inputMode="decimal" {...labour.register('hourlyRate')} />
        </Field>
        <Button type="submit" className="sm:mt-5" disabled={labour.formState.isSubmitting}>
          Add labour
        </Button>
      </form>
    </div>
  );
}
