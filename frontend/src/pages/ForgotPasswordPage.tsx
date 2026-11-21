import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { z } from 'zod';

import { authApi } from '@/api/auth';
import { ApiError, errorMessage } from '@/api/errors';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';

const schema = z.object({ email: z.email('Enter a valid email address') });

type ForgotValues = z.infer<typeof schema>;

/** "Forgot password?": asks for the account's e-mail and sends a reset link to it. */
export function ForgotPasswordPage() {
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotValues>({ resolver: zodResolver(schema), defaultValues: { email: '' } });

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null);
    try {
      await authApi.forgotPassword({ email });
      setSentTo(email);
    } catch (error) {
      setFormError(error instanceof ApiError && error.isClientError ? error.message : errorMessage(error));
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>Forgot your password?</CardTitle>
        <CardDescription>
          Enter the email address of your account and we'll send you a link to choose a new password.
        </CardDescription>
      </CardHeader>
      {sentTo ? (
        <>
          <CardContent>
            <Alert variant="info">
              If an account uses <strong>{sentTo}</strong>, we've sent it a link. The link works for 60
              minutes; check your spam folder if it doesn't arrive in a few minutes.
            </Alert>
          </CardContent>
          <CardFooter>
            <Link to="/login" className="text-sm font-medium text-primary hover:underline">
              Back to sign in
            </Link>
          </CardFooter>
        </>
      ) : (
        <form onSubmit={onSubmit} noValidate aria-label="Forgot password">
          <CardContent className="grid gap-4">
            {formError && <Alert>{formError}</Alert>}
            <Field label="Email" error={errors.email?.message}>
              <Input type="email" autoComplete="email" autoFocus {...register('email')} />
            </Field>
          </CardContent>
          <CardFooter className="flex-col gap-3">
            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting && <Spinner className="text-primary-foreground" />}
              Send reset link
            </Button>
            <Link to="/login" className="text-sm font-medium text-primary hover:underline">
              Back to sign in
            </Link>
          </CardFooter>
        </form>
      )}
    </Card>
  );
}
