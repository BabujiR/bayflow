import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import type { ProfileRequest } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';

export const profileSchema = z.object({
  name: z.string().trim().min(1, 'Enter a name').max(100, 'At most 100 characters'),
  email: z.email('Enter a valid email address'),
  phone: z
    .string()
    .trim()
    .refine((v) => v === '' || /^[+0-9 ()-]{5,30}$/.test(v), 'Enter a valid phone number'),
});

export type ProfileValues = z.infer<typeof profileSchema>;

/** Name, email and phone; used for a customer's own profile and by advisors for new customers. */
export function ProfileForm({
  defaultValues,
  submitLabel,
  onSubmit,
}: {
  defaultValues?: Partial<ProfileValues>;
  submitLabel: string;
  onSubmit: (values: ProfileRequest) => Promise<unknown>;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { name: '', email: '', phone: '', ...defaultValues },
  });

  return (
    <form
      noValidate
      className="grid gap-4"
      onSubmit={handleSubmit(async ({ name, email, phone }) => {
        await onSubmit({ name, email, phone: phone || undefined }).catch(() => undefined);
      })}
    >
      <Field label="Full name" error={errors.name?.message}>
        <Input autoComplete="name" {...register('name')} />
      </Field>
      <Field label="Email" error={errors.email?.message}>
        <Input type="email" autoComplete="email" {...register('email')} />
      </Field>
      <Field label="Phone" error={errors.phone?.message} hint="Optional, so the workshop can call you.">
        <Input type="tel" autoComplete="tel" {...register('phone')} />
      </Field>
      <Button type="submit" disabled={isSubmitting} className="justify-self-start">
        {submitLabel}
      </Button>
    </form>
  );
}
