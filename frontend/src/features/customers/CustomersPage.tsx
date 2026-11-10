import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router';

import { customersApi } from '@/api/endpoints';
import { useAuth } from '@/auth/auth-context';
import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Pager } from '@/components/common/Pager';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate } from '@/lib/format';
import { useAction } from '@/lib/use-action';

import { ProfileForm } from './ProfileForm';

export function CustomersPage() {
  const { hasRole } = useAuth();
  const advisor = hasRole('SERVICE_ADVISOR');
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const params = { q: q.trim() || undefined, page, size: 20 };
  const customers = useQuery({ queryKey: ['customers', params], queryFn: () => customersApi.list(params) });
  const create = useAction(customersApi.create, {
    success: (c) => `${c.name} added`,
    invalidate: [['customers']],
    onSuccess: (c) => {
      setCreating(false);
      navigate(`/customers/${c.id}`);
    },
  });

  return (
    <>
      <PageHeader
        title="Customers"
        description="Find a customer to see their vehicles, bookings and history."
        actions={
          advisor && (
            <Dialog open={creating} onOpenChange={setCreating}>
              <DialogTrigger asChild>
                <Button>New customer</Button>
              </DialogTrigger>
              <DialogContent title="New customer" description="For customers booking by phone or at the desk.">
                <ProfileForm submitLabel="Create customer" onSubmit={(values) => create.mutateAsync(values)} />
              </DialogContent>
            </Dialog>
          )
        }
      />
      <div className="mb-4 grid max-w-md gap-1.5">
        <Label htmlFor="customer-search">Search</Label>
        <Input
          id="customer-search"
          type="search"
          placeholder="Name, email or phone"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
        />
      </div>
      <QueryState query={customers}>
        {(result) =>
          result.content.length === 0 ? (
            <EmptyState title="No customers found" description={q ? 'Try another search.' : undefined} />
          ) : (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Phone</TableHead>
                    <TableHead>Account</TableHead>
                    <TableHead>Since</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {result.content.map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>
                        <Link to={`/customers/${c.id}`} className="font-medium text-primary hover:underline">
                          {c.name}
                        </Link>
                      </TableCell>
                      <TableCell>{c.email}</TableCell>
                      <TableCell>{c.phone ?? '—'}</TableCell>
                      <TableCell>{c.userId ? 'Online account' : 'Walk-in'}</TableCell>
                      <TableCell>{formatDate(c.createdAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <Pager page={result.page} totalPages={result.totalPages} onChange={setPage} />
            </>
          )
        }
      </QueryState>
    </>
  );
}
