import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { z } from 'zod';

import { ApiError, errorMessage } from '@/api/errors';
import { useAuth } from '@/auth/auth-context';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';

// Same length rules as auth-service.
const schema = z
  .object({
    newPassword: z.string().min(8, 'At least 8 characters').max(100, 'At most 100 characters'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type ResetValues = z.infer<typeof schema>;

/** The page an e-mailed reset link opens: choose a new password, then you're signed in. */
export function ResetPasswordPage() {
  const { resetPassword } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  // Read once, then taken out of the address bar (and the browser history).
  const [token] = useState(() => params.get('token') ?? '');
  const [invalid, setInvalid] = useState(!token);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetValues>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  });

  useEffect(() => {
    if (window.location.search.includes('token=')) {
      window.history.replaceState(window.history.state, '', window.location.pathname);
    }
  }, []);

  const onSubmit = handleSubmit(async ({ newPassword }) => {
    setFormError(null);
    try {
      await resetPassword({ token, newPassword });
      toast.success('Password changed. You are signed in.');
      navigate('/', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.code === 'reset-link-invalid') {
        setInvalid(true);
      } else {
        setFormError(errorMessage(error));
      }
    }
  });

  if (invalid) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>This link doesn't work any more</CardTitle>
          <CardDescription>
            Reset links work once, for 60 minutes, and only the newest one you asked for.
          </CardDescription>
        </CardHeader>
        <CardFooter className="flex-col gap-3">
          <Button asChild className="w-full">
            <Link to="/forgot-password">Send a new link</Link>
          </Button>
          <Link to="/login" className="text-sm font-medium text-primary hover:underline">
            Back to sign in
          </Link>
        </CardFooter>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Choose a new password</CardTitle>
        <CardDescription>You'll be signed in, and signed out on your other devices.</CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate aria-label="Reset password">
        <CardContent className="grid gap-4">
          {formError && <Alert>{formError}</Alert>}
          <Field label="New password" error={errors.newPassword?.message} hint="At least 8 characters.">
            <Input type="password" autoComplete="new-password" autoFocus {...register('newPassword')} />
          </Field>
          <Field label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
          </Field>
        </CardContent>
        <CardFooter>
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting && <Spinner className="text-primary-foreground" />}
            Save new password
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
