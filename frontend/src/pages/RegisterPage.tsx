import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router';
import { z } from 'zod';

import { ApiError, errorMessage } from '@/api/errors';
import { useAuth } from '@/auth/auth-context';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';

// Same rules as auth-service, so most mistakes are caught before the request.
const schema = z
  .object({
    username: z
      .string()
      .trim()
      .min(3, 'At least 3 characters')
      .max(50, 'At most 50 characters')
      .regex(/^[A-Za-z0-9._-]+$/, "Only letters, digits, '.', '_' and '-'"),
    email: z.email('Enter a valid email address'),
    password: z.string().min(8, 'At least 8 characters').max(100, 'At most 100 characters'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.password === v.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type RegisterValues = z.infer<typeof schema>;

export function RegisterPage() {
  const { register: createAccount } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: '', email: '', password: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ username, email, password }) => {
    setFormError(null);
    try {
      await createAccount({ username, email, password });
      navigate('/', { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === 'username-taken') {
          setError('username', { message: error.message });
          return;
        }
        if (error.code === 'email-taken') {
          setError('email', { message: error.message });
          return;
        }
        for (const [field, message] of Object.entries(error.fieldErrors)) {
          if (field === 'username' || field === 'email' || field === 'password') {
            setError(field, { message });
          }
        }
        if (Object.keys(error.fieldErrors).length > 0) {
          return;
        }
      }
      setFormError(errorMessage(error));
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Create your account</CardTitle>
        <CardDescription>Book services, approve work and keep your vehicle's history in one place.</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate>
        <CardContent className="grid gap-4">
          {formError && <Alert>{formError}</Alert>}
          <Field label="Username" error={errors.username?.message}>
            <Input autoComplete="username" autoFocus {...register('username')} />
          </Field>
          <Field label="Email" error={errors.email?.message}>
            <Input type="email" autoComplete="email" {...register('email')} />
          </Field>
          <Field label="Password" error={errors.password?.message} hint="At least 8 characters.">
            <Input type="password" autoComplete="new-password" {...register('password')} />
          </Field>
          <Field label="Confirm password" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
          </Field>
        </CardContent>
        <CardFooter className="flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting && <Spinner className="text-primary-foreground" />}
            Create account
          </Button>
          <p className="text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/login" className="font-medium text-primary hover:underline">
              Sign in
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}
