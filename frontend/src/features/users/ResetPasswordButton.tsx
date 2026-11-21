import { useState } from 'react';
import { toast } from 'sonner';

import { usersApi } from '@/api/endpoints';
import type { TemporaryPassword } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
import { formatDateTime } from '@/lib/format';
import { useAction } from '@/lib/use-action';

/**
 * Resets a forgotten password: the account gets a temporary password, shown here once, which
 * the person replaces at their next sign-in. Admins reset anyone else's; advisors and managers
 * a customer's. The server enforces who may reset whom.
 */
export function ResetPasswordButton({ userId, name }: { userId: string; name: string }) {
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<TemporaryPassword | null>(null);
  const reset = useAction(() => usersApi.resetPassword(userId), { onSuccess: setResult });

  const close = (next: boolean) => {
    setOpen(next);
    if (!next) {
      // The temporary password is shown once: closing forgets it.
      setResult(null);
    }
  };

  const copy = async () => {
    if (!result) {
      return;
    }
    try {
      await navigator.clipboard.writeText(result.temporaryPassword);
      toast.success('Temporary password copied');
    } catch {
      toast.error('Could not copy; select it and copy it instead.');
    }
  };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline" aria-label={`Reset password for ${name}`}>
          Reset password
        </Button>
      </DialogTrigger>
      <DialogContent
        title={result ? 'Temporary password' : `Reset password for ${name}?`}
        description={
          result
            ? `Give it to ${name} in person or by phone, not by email. They choose their own password when they sign in.`
            : `${name} gets a temporary password and is signed out everywhere. Use this when they have forgotten their password or are locked out.`
        }
      >
        {result ? (
          <div className="grid gap-4">
            <p
              className="select-all rounded-lg border bg-muted px-4 py-3 text-center font-mono text-2xl font-semibold tracking-widest"
              data-testid="temporary-password"
            >
              {result.temporaryPassword}
            </p>
            <p className="text-sm text-muted-foreground">
              Username: <strong>{result.username}</strong> · works until {formatDateTime(result.expiresAt)}.
              It is shown only now.
            </p>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => void copy()}>
                Copy
              </Button>
              <Button onClick={() => close(false)}>Done</Button>
            </div>
          </div>
        ) : (
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => close(false)}>
              Cancel
            </Button>
            <Button onClick={() => reset.mutate(undefined)} disabled={reset.isPending}>
              Reset password
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
