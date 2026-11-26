import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { avatarApi, usersApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { refreshSession } from '@/api/http';
import type { Role, User } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { rolesSummary, STAFF_ROLES, worksAtChosenLocations } from '@/auth/roles';
import { PageHeader } from '@/components/common/PageHeader';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { LocationChecklist } from '@/features/locations/LocationChecklist';
import { useLocationLookup, useLocations } from '@/features/locations/use-locations';
import { formatDate } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { ResetPasswordButton } from './ResetPasswordButton';
import { RoleChecklist } from './RoleChecklist';

const staffSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, 'At least 3 characters')
    .max(50, 'At most 50 characters')
    .regex(/^[A-Za-z0-9._-]+$/, "Only letters, digits, '.', '_' and '-'"),
  email: z.email('Enter a valid email address'),
  password: z.string().min(8, 'At least 8 characters').max(100),
});

type StaffValues = z.infer<typeof staffSchema>;

/** Works at chosen locations (an ADMIN works with all of them). */
const isLocationScoped = (u: User) => worksAtChosenLocations(u.roles);

/** Staff accounts (customers' roles never change here). */
const isStaff = (u: User) => u.roles.every((r) => STAFF_ROLES.includes(r));

/** The team (staff only): admins create accounts, set roles and locations, and enable or disable them. */
export function UsersPage() {
  const { user: me } = useAuth();
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  // The team only: customers' accounts are on each customer's page.
  const params = { page, size: 20, sort: 'createdAt,desc', kind: 'staff' as const };
  const lookup = useLocationLookup();
  const users = useQuery({ queryKey: ['users', params], queryFn: () => usersApi.list(params) });
  const setEnabled = useAction(
    ({ id, enabled }: { id: string; enabled: boolean }) => usersApi.setEnabled(id, enabled),
    {
      success: (u) => `${u.username} ${u.enabled ? 'enabled' : 'disabled'}`,
      invalidate: [['users']],
    },
  );
  // An unsuitable photo: the user is shown by their initials again (they can choose anew).
  const resetAvatar = useAction((id: string) => avatarApi.reset(id), {
    success: (u) => `${u.username} is shown by their initials again`,
    invalidate: [['users']],
  });

  return (
    <>
      <PageHeader
        title="Team"
        description="Your staff and what each person does. Reset password gives someone who forgot theirs a temporary one; disabling an account signs it out everywhere. Customers' accounts are on each customer's page."
        actions={
          <Dialog open={creating} onOpenChange={setCreating}>
            <DialogTrigger asChild>
              <Button>New staff account</Button>
            </DialogTrigger>
            <DialogContent
              title="New staff account"
              description="Share the password with them securely."
              className="max-w-xl"
            >
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
                  {lookup.hasSeveral && <TableHead>Locations</TableHead>}
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
                    <TableCell className="font-medium">
                      <span className="flex items-center gap-2">
                        <UserAvatar name={u.username} avatar={u.avatar} />
                        {u.username}
                      </span>
                    </TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{rolesSummary(u.roles)}</TableCell>
                    {lookup.hasSeveral && (
                      <TableCell>
                        {isLocationScoped(u)
                          ? (u.locationIds ?? []).map((id) => lookup.nameOf(id)).join(', ') || '—'
                          : 'All'}
                      </TableCell>
                    )}
                    <TableCell>
                      <Badge variant={u.enabled ? 'success' : 'outline'}>
                        {u.enabled ? 'Active' : 'Disabled'}
                      </Badge>
                    </TableCell>
                    <TableCell>{formatDate(u.createdAt)}</TableCell>
                    <TableCell className="text-right">
                      {isStaff(u) && <EditRoles user={u} self={u.id === me?.id} />}
                      {lookup.hasSeveral && isLocationScoped(u) && <EditLocations user={u} />}
                      {u.id !== me?.id && u.avatar?.kind === 'PHOTO' && (
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={resetAvatar.isPending}
                          onClick={() => resetAvatar.mutate(u.id)}
                          aria-label={`Remove ${u.username}'s photo`}
                        >
                          Remove photo
                        </Button>
                      )}
                      {u.id !== me?.id && <ResetPasswordButton userId={u.id} name={u.username} />}
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
    defaultValues: { username: '', email: '', password: '' },
  });
  const active = (useLocations().data ?? []).filter((l) => l.status === 'ACTIVE');
  const [roles, setRoles] = useState<Role[]>(['MECHANIC']);
  const [rolesError, setRolesError] = useState<string | undefined>();
  const [locationIds, setLocationIds] = useState<string[]>([]);
  const [locationError, setLocationError] = useState<string | undefined>();
  const choosesLocations = active.length > 1 && worksAtChosenLocations(roles);
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
        if (roles.length === 0) {
          setRolesError('Choose at least one role');
          return;
        }
        if (choosesLocations && locationIds.length === 0) {
          setLocationError('Choose at least one location');
          return;
        }
        await create
          .mutateAsync({
            ...values,
            roles: ordered(roles),
            locationIds: choosesLocations ? locationIds : undefined,
          })
          .catch(() => undefined);
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
      <RoleChecklist
        value={roles}
        error={rolesError}
        onChange={(next) => {
          setRoles(next);
          setRolesError(undefined);
        }}
      />
      {choosesLocations && (
        <LocationChecklist
          locations={active}
          value={locationIds}
          error={locationError}
          onChange={(ids) => {
            setLocationIds(ids);
            setLocationError(undefined);
          }}
        />
      )}
      <Button type="submit" disabled={isSubmitting} className="justify-self-start">
        Create account
      </Button>
    </form>
  );
}

/** Roles in the usual order (Admin, Manager, Service advisor, Mechanic). */
const ordered = (roles: Role[]) => STAFF_ROLES.filter((r) => roles.includes(r));

/**
 * Changes what a staff member does: e.g. an owner who also runs the desk and works on cars.
 * Your own admin role stays (another admin can remove it). From their next token (15 minutes);
 * your own change applies at once.
 */
function EditRoles({ user, self }: { user: User; self: boolean }) {
  const { updateUser } = useAuth();
  const [open, setOpen] = useState(false);
  const active = (useLocations().data ?? []).filter((l) => l.status === 'ACTIVE');
  const [roles, setRoles] = useState<Role[]>(user.roles);
  const [ids, setIds] = useState<string[]>(user.locationIds ?? []);
  const [error, setError] = useState<string | undefined>();
  const [locationError, setLocationError] = useState<string | undefined>();
  const choosesLocations = active.length > 1 && worksAtChosenLocations(roles);
  const save = useAction(
    () =>
      usersApi.setRoles(user.id, {
        roles: ordered(roles),
        locationIds: choosesLocations ? ids : undefined,
      }),
    {
      success: (u) => `${u.username} is now ${rolesSummary(u.roles)}`,
      invalidate: [['users']],
      onSuccess: (saved) => {
        setOpen(false);
        if (self) {
          // New menus at once: the new token carries the new roles.
          updateUser(saved);
          void refreshSession();
        }
      },
    },
  );

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setRoles(user.roles);
          setIds(user.locationIds ?? []);
          setError(undefined);
          setLocationError(undefined);
        }
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" aria-label={`Edit roles of ${user.username}`}>
          Roles
        </Button>
      </DialogTrigger>
      <DialogContent
        className="max-w-xl"
        title={`Roles of ${user.username}`}
        description="Everything this person does. They see each role's pages; changes apply within 15 minutes."
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (roles.length === 0) {
              setError('Choose at least one role');
              return;
            }
            if (choosesLocations && ids.length === 0) {
              setLocationError('Choose at least one location');
              return;
            }
            save.mutate(undefined);
          }}
        >
          <RoleChecklist
            value={roles}
            error={error}
            locked={self && user.roles.includes('ADMIN') ? ['ADMIN'] : []}
            onChange={(next) => {
              setRoles(next);
              setError(undefined);
            }}
          />
          {choosesLocations && (
            <LocationChecklist
              locations={active}
              value={ids}
              error={locationError}
              onChange={(next) => {
                setIds(next);
                setLocationError(undefined);
              }}
            />
          )}
          <Button type="submit" disabled={save.isPending} className="justify-self-start">
            Save roles
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Changes where a manager, advisor or mechanic works (applies from their next sign-in refresh). */
function EditLocations({ user }: { user: User }) {
  const [open, setOpen] = useState(false);
  const active = (useLocations().data ?? []).filter((l) => l.status === 'ACTIVE');
  const [ids, setIds] = useState<string[]>(user.locationIds ?? []);
  const [error, setError] = useState<string | undefined>();
  const save = useAction((locationIds: string[]) => usersApi.setLocations(user.id, locationIds), {
    success: (u) => `${u.username}'s locations saved`,
    invalidate: [['users']],
    onSuccess: () => setOpen(false),
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (next) {
          setIds(user.locationIds ?? []);
          setError(undefined);
        }
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" aria-label={`Edit locations of ${user.username}`}>
          Locations
        </Button>
      </DialogTrigger>
      <DialogContent
        title={`Locations of ${user.username}`}
        description="They see and work with these locations only. Changes apply within 15 minutes."
      >
        <form
          noValidate
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (ids.length === 0) {
              setError('Choose at least one location');
              return;
            }
            save.mutate(ids);
          }}
        >
          <LocationChecklist locations={active} value={ids} onChange={setIds} error={error} />
          <Button type="submit" disabled={save.isPending} className="justify-self-start">
            Save locations
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
