import { useState } from 'react';
import { toast } from 'sonner';

import { consoleApi } from '@/api/endpoints';
import type { IssuedInvitation } from '@/api/types';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { formatDate } from '@/lib/format';
import { useAction } from '@/lib/use-action';

export const INVITATIONS_KEY = ['console', 'invitations'] as const;
const DEMO_KEY = ['console', 'demo-requests'] as const;

export interface InviteDraft {
  email: string;
  contactName: string;
  shopName: string;
  demoRequestId: string | null;
}

/** "Send setup invite": who gets the link; the shop name fills in the sign-up form. */
export function InviteDialog({
  draft,
  onClose,
  onIssued,
}: {
  draft: InviteDraft;
  onClose: () => void;
  onIssued: (issued: IssuedInvitation) => void;
}) {
  const [email, setEmail] = useState(draft.email);
  const [contactName, setContactName] = useState(draft.contactName);
  const [shopName, setShopName] = useState(draft.shopName);
  const validEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
  const send = useAction(
    () =>
      consoleApi.createInvitation({
        email: email.trim(),
        contactName: contactName.trim() || null,
        shopName: shopName.trim() || null,
        demoRequestId: draft.demoRequestId,
      }),
    { invalidate: [[...INVITATIONS_KEY], [...DEMO_KEY]], onSuccess: onIssued },
  );
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent
        title="Send setup invite"
        description="The owner gets a link to create their workshop and admin account. It works once, for 7 days."
      >
        <form
          noValidate
          aria-label="Send setup invite"
          className="grid gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (validEmail) {
              send.mutate(undefined);
            }
          }}
        >
          <Field
            label="Owner's email"
            error={email && !validEmail ? 'Enter a valid email address' : undefined}
          >
            <Input type="email" autoFocus value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <Field label="Contact name">
            <Input value={contactName} maxLength={120} onChange={(e) => setContactName(e.target.value)} />
          </Field>
          <Field label="Shop name" hint="Filled in on their sign-up form; they can change it.">
            <Input value={shopName} maxLength={120} onChange={(e) => setShopName(e.target.value)} />
          </Field>
          <Button type="submit" className="justify-self-start" disabled={!validEmail || send.isPending}>
            {send.isPending ? 'Sending…' : 'Send invite'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** After sending or resending: the setup link, shown this once. */
export function IssuedDialog({ issued, onClose }: { issued: IssuedInvitation; onClose: () => void }) {
  const { invitation, setupUrl, emailed } = issued;
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent title="Setup link ready" description={`For ${invitation.email}`}>
        <div className="grid gap-4 text-sm">
          <p role="status">
            {emailed
              ? `E-mailed to ${invitation.email}. It works until ${formatDate(invitation.expiresAt)}.`
              : `E-mail isn't set up here, so nothing was sent. Copy the link and send it to ${invitation.email} yourself. It works until ${formatDate(invitation.expiresAt)}.`}
          </p>
          <div className="flex gap-2">
            <Input readOnly aria-label="Setup link" value={setupUrl} onFocus={(e) => e.target.select()} />
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void navigator.clipboard?.writeText(setupUrl).then(
                  () => toast.success('Setup link copied'),
                  () => toast.error('Copy failed: select the link and copy it'),
                );
              }}
            >
              Copy
            </Button>
          </div>
          <p className="text-muted-foreground">
            This is the only time the link is shown. Resend the invite for a new one (the old link then stops
            working).
          </p>
          <Button className="justify-self-start" onClick={onClose}>
            Done
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
