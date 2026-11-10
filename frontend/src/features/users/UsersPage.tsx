import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { usersApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { User } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { ROLE_LABELS } from '@/auth/roles';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate } from '@/lib/format';
import { useAction } from '@/lib/use-action';

const STAFF_ROLES = ['SERVICE_ADVISOR', 'MECHANIC', 'ADMIN'] as const;

const staffSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, 'At least 3 characters')
    .max(50, 'At most 50 characters')
    .regex(/^[A-Za-z0-9._-]+$/, "Only letters, digits, '.', '_' and '-'"),
  email: z.email('Enter a valid email address'),
  password: z.string().min(8, 'At least 8 characters').max(100),
  role: z.enum(STAFF_ROLES),
});

type StaffValues = z.infer<typeof staffSchema>;

/** Admins create staff accounts and enable or disable any account. */
export function UsersPage() {
  const { user: me } = useAuth();
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const params = { page, size: 20, sort: 'createdAt,desc' };
  const users = useQuery({ queryKey: ['users', params], queryFn: () => usersApi.list(params) });
  const setEnabled = useAction(({ id, enabled }: { id: string; enabled: boolean }) => usersApi.setEnabled(id, enabled), {
    success: (u) => `${u.username} ${u.enabled ? 'enabled' : 'disabled'}`,
    invalidate: [['users']],
  });

  return (
    <>
      <PageHeader
        title="User accounts"
        description="Customers register themselves; staff accounts are created here. Disabling an account signs it out everywhere."
        actions={
          <Dialog open={creating} onOpenChange={setCreating}>
            <DialogTrigger asChild>
              <Button>New staff account</Button>
            </DialogTrigger>
            <DialogContent title="New staff account" description="Share the password with them securely.">
              <StaffForm onCreated={() => setCreating(false)} />
            </DialogContent>
          </Dialog>
        }
      />
      <QueryState query={users}>
        {(result) => (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Username</TableHead>
                  <TableHead>Email</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {result.content.map((u: User) => (
                  <TableRow key={u.id}>
                    <TableCell className="font-medium">{u.username}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{u.roles.map((r) => ROLE_LABELS[r]).join(', ')}</TableCell>
                    <TableCell>
                      <Badge variant={u.enabled ? 'success' : 'outline'}>{u.enabled ? 'Active' : 'Disabled'}</Badge>
                    </TableCell>
                    <TableCell>{formatDate(u.createdAt)}</TableCell>
                    <TableCell className="text-right">
                      {u.id !== me?.id && (
                        <Button
                          size="sm"
                          variant={u.enabled ? 'outline' : 'default'}
                          disabled={setEnabled.isPending}
                          onClick={() => setEnabled.mutate({ id: u.id, enabled: !u.enabled })}
                          aria-label={`${u.enabled ? 'Disable' : 'Enable'} ${u.username}`}
                        >
                          {u.enabled ? 'Disable' : 'Enable'}
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}

function StaffForm({ onCreated }: { onCreated: () => void }) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<StaffValues>({
    resolver: zodResolver(staffSchema),
    defaultValues: { username: '', email: '', password: '', role: 'MECHANIC' },
  });
  const create = useAction(usersApi.create, {
    success: (u) => `Account ${u.username} created`,
    invalidate: [['users']],
    onSuccess: onCreated,
    onError: (error) => {
      if (error instanceof ApiError && (error.code === 'username-taken' || error.code === 'email-taken')) {
        setError(error.code === 'username-taken' ? 'username' : 'email', { message: error.message });
        return true;
      }
      return false;
    },
  });

  return (
    <form
      noValidate
      className="grid gap-4"
      onSubmit={handleSubmit(async (values) => {
        await create.mutateAsync(values).catch(() => undefined);
      })}
    >
      <Field label="Username" error={errors.username?.message}>
        <Input autoComplete="off" {...register('username')} />
      </Field>
      <Field label="Email" error={errors.email?.message}>
        <Input type="email" autoComplete="off" {...register('email')} />
      </Field>
      <Field label="Temporary password" error={errors.password?.message}>
        <Input type="password" autoComplete="new-password" {...register('password')} />
      </Field>
      <Field label="Role" error={errors.role?.message}>
        <Select {...register('role')}>
          {STAFF_ROLES.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role]}
            </option>
          ))}
        </Select>
      </Field>
      <Button type="submit" disabled={isSubmitting} className="justify-self-start">
        Create account
      </Button>
    </form>
  );
}
