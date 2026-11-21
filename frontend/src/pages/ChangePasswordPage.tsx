import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router';
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
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z.string().min(8, 'At least 8 characters').max(100, 'At most 100 characters'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    message: 'Choose a new password that is different from the current one',
    path: ['newPassword'],
  });

type ChangePasswordValues = z.infer<typeof schema>;

/**
 * Change your own password. After a staff reset (signed in with a temporary password) this is
 * the only page available until it's done.
 */
export function ChangePasswordPage() {
  const { user, changePassword, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const forced = !!user?.mustChangePassword;
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    setFormError(null);
    try {
      await changePassword({ currentPassword, newPassword });
      toast.success('Password changed');
      navigate(from && from.startsWith('/') && from !== location.pathname ? from : '/', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.code === 'current-password-incorrect') {
        setError('currentPassword', { message: error.message });
      } else if (error instanceof ApiError && error.code === 'password-unchanged') {
        setError('newPassword', { message: error.message });
      } else {
        setFormError(errorMessage(error));
      }
    }
  });

  const signOut = async () => {
    try {
      await logout();
    } finally {
      navigate('/login', { replace: true });
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{forced ? 'Choose a new password' : 'Change password'}</CardTitle>
        <CardDescription>
          {forced
            ? 'You signed in with a temporary password from the workshop. Choose your own to continue.'
            : 'Your other devices will be signed out.'}
        </CardDescription>
      </CardHeader>
      <form onSubmit={onSubmit} noValidate aria-label="Change password">
        <CardContent className="grid gap-4">
          {formError && <Alert>{formError}</Alert>}
          <Field
            label={forced ? 'Temporary password' : 'Current password'}
            error={errors.currentPassword?.message}
          >
            <Input
              type="password"
              autoComplete="current-password"
              autoFocus
              {...register('currentPassword')}
            />
          </Field>
          <Field label="New password" error={errors.newPassword?.message} hint="At least 8 characters.">
            <Input type="password" autoComplete="new-password" {...register('newPassword')} />
          </Field>
          <Field label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...register('confirmPassword')} />
          </Field>
        </CardContent>
        <CardFooter className="flex-col gap-3">
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting && <Spinner className="text-primary-foreground" />}
            Save new password
          </Button>
          {forced ? (
            <Button type="button" variant="ghost" onClick={() => void signOut()}>
              Sign out
            </Button>
          ) : (
            <Link to="/" className="text-sm font-medium text-primary hover:underline">
              Cancel
            </Link>
          )}
        </CardFooter>
      </form>
    </Card>
  );
}
