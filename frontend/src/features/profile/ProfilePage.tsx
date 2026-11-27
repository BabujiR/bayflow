import { useEffect, useRef, useState } from 'react';

import { avatarApi } from '@/api/endpoints';
import type { Avatar, User } from '@/api/types';
import { useAuth } from '@/auth/auth-context';
import { ROLE_LABELS } from '@/auth/roles';
import { PageHeader } from '@/components/common/PageHeader';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAction } from '@/lib/use-action';
import { cn } from '@/lib/utils';

import { AVATAR_CHARACTERS, CHARACTER_CODES } from './avatar-characters';

/** Photos bigger than this aren't even read (the server takes up to 2 MB after cropping). */
const MAX_PICKED_BYTES = 15 * 1024 * 1024;
const PHOTO_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

/**
 * The centre square of the picked photo as a 512 px JPEG (smaller to send; the server cuts and
 * re-encodes it again). Where the browser can't draw it, the file goes as it is.
 */
async function squarePhoto(file: File): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file);
    const side = Math.min(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = Math.min(512, side);
    const context = canvas.getContext('2d');
    if (!context) {
      return file;
    }
    context.drawImage(
      bitmap,
      (bitmap.width - side) / 2,
      (bitmap.height - side) / 2,
      side,
      side,
      0,
      0,
      canvas.width,
      canvas.height,
    );
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9));
    return blob ?? file;
  } catch {
    return file;
  }
}

/** The signed-in user's own page: who they are, and how they show in the app (their avatar). */
export function ProfilePage() {
  const { user } = useAuth();
  if (!user) {
    return null;
  }
  return (
    <>
      <PageHeader title="My profile" description="How you show to your team and customers in Ramplight." />
      <div className="grid max-w-3xl gap-6">
        <AvatarCard user={user} />
        <Card>
          <CardHeader>
            <CardTitle>Account</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-2 text-sm sm:grid-cols-[8rem_1fr]">
              <dt className="text-muted-foreground">Username</dt>
              <dd className="font-medium">{user.username}</dd>
              <dt className="text-muted-foreground">Email</dt>
              <dd>{user.email}</dd>
              <dt className="text-muted-foreground">Role</dt>
              <dd>{user.roles.map((r) => ROLE_LABELS[r]).join(', ')}</dd>
            </dl>
          </CardContent>
        </Card>
      </div>
    </>
  );
}

type Mode = 'INITIALS' | 'CHARACTER' | 'PHOTO';

function AvatarCard({ user }: { user: User }) {
  const { updateUser } = useAuth();
  const current: Avatar = user.avatar ?? { kind: 'INITIALS', character: null, photoUrl: null };
  const [mode, setMode] = useState<Mode>(current.kind);
  const [character, setCharacter] = useState<string | null>(current.character);
  const [picked, setPicked] = useState<{ blob: Blob; url: string } | null>(null);
  const [pickError, setPickError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  // The preview's object URL is freed when replaced or when leaving the page.
  useEffect(
    () => () => {
      if (picked) {
        URL.revokeObjectURL(picked.url);
      }
    },
    [picked],
  );

  const save = useAction(
    async () => {
      if (mode === 'PHOTO') {
        return avatarApi.upload(picked!.blob);
      }
      return avatarApi.choose(
        mode === 'CHARACTER' ? { kind: 'CHARACTER', character: character! } : { kind: 'INITIALS' },
      );
    },
    {
      success: 'Your avatar is saved',
      invalidate: [['users']],
      onSuccess: (saved) => {
        updateUser(saved);
        setPicked(null);
      },
    },
  );

  const preview: Avatar =
    mode === 'PHOTO'
      ? { kind: 'PHOTO', character: null, photoUrl: picked?.url ?? current.photoUrl }
      : mode === 'CHARACTER'
        ? { kind: character ? 'CHARACTER' : 'INITIALS', character, photoUrl: null }
        : { kind: 'INITIALS', character: null, photoUrl: null };
  const changed =
    mode !== current.kind ||
    (mode === 'CHARACTER' && character !== current.character) ||
    (mode === 'PHOTO' && picked !== null);
  const ready =
    changed && (mode !== 'CHARACTER' || character !== null) && (mode !== 'PHOTO' || picked !== null);

  const pick = async (file: File | undefined) => {
    setPickError(null);
    if (!file) {
      return;
    }
    if (!PHOTO_TYPES.includes(file.type)) {
      setPickError('Choose a JPEG, PNG or WebP photo.');
      return;
    }
    if (file.size > MAX_PICKED_BYTES) {
      setPickError('That photo is too big. Choose one under 15 MB.');
      return;
    }
    const blob = await squarePhoto(file);
    if (blob.size > 2 * 1024 * 1024) {
      setPickError('That photo is too big. Choose a smaller one.');
      return;
    }
    setPicked({ blob, url: URL.createObjectURL(blob) });
    setMode('PHOTO');
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your avatar</CardTitle>
        <CardDescription>Shown next to your name: in the header, on the board and in lists.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6 sm:grid-cols-[auto_1fr]">
        <div className="grid justify-items-center gap-2">
          <span key={`${preview.kind}-${preview.character}-${preview.photoUrl}`} className="gb-pop">
            <UserAvatar name={user.username} avatar={preview} size="xl" animated label="Avatar preview" />
          </span>
          <span className="text-sm font-medium">{user.username}</span>
        </div>
        <div className="grid content-start gap-4">
          <div
            role="radiogroup"
            aria-label="Avatar style"
            className="grid grid-cols-3 rounded-lg bg-muted p-1"
          >
            {(['INITIALS', 'CHARACTER', 'PHOTO'] as Mode[]).map((m) => (
              <button
                key={m}
                type="button"
                role="radio"
                aria-checked={mode === m}
                onClick={() => setMode(m)}
                className={cn(
                  'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                  mode === m ? 'bg-background shadow-sm' : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {m === 'INITIALS' ? 'Initials' : m === 'CHARACTER' ? 'Character' : 'Photo'}
              </button>
            ))}
          </div>

          {mode === 'INITIALS' && (
            <p className="text-sm text-muted-foreground">
              Your initials in a colour of their own. Simple and tidy.
            </p>
          )}

          {mode === 'CHARACTER' && (
            <div className="grid grid-cols-4 gap-2 sm:grid-cols-6" role="group" aria-label="Characters">
              {CHARACTER_CODES.map((code) => (
                <button
                  key={code}
                  type="button"
                  aria-pressed={character === code}
                  aria-label={AVATAR_CHARACTERS[code].label}
                  title={AVATAR_CHARACTERS[code].label}
                  onClick={() => setCharacter(code)}
                  className={cn(
                    'grid justify-items-center rounded-lg border p-2 transition-all hover:border-primary/60',
                    character === code && 'border-primary bg-primary/5 ring-1 ring-primary',
                  )}
                >
                  <UserAvatar
                    name={user.username}
                    avatar={{ kind: 'CHARACTER', character: code, photoUrl: null }}
                    size="md"
                    animated={character === code}
                  />
                </button>
              ))}
            </div>
          )}

          {mode === 'PHOTO' && (
            <div className="grid gap-2">
              <input
                ref={fileInput}
                type="file"
                accept={PHOTO_TYPES.join(',')}
                className="sr-only"
                aria-label="Choose a photo"
                onChange={(e) => {
                  void pick(e.target.files?.[0]);
                  e.target.value = '';
                }}
              />
              <Button
                type="button"
                variant="outline"
                className="justify-self-start"
                onClick={() => fileInput.current?.click()}
              >
                {picked || current.photoUrl ? 'Choose another photo' : 'Choose a photo'}
              </Button>
              <p className="text-sm text-muted-foreground">
                JPEG, PNG or WebP. We use the middle square, and remove details such as where it was taken.
              </p>
              {pickError && <Alert>{pickError}</Alert>}
            </div>
          )}

          <div className="flex gap-2">
            <Button type="button" disabled={!ready || save.isPending} onClick={() => save.mutate(undefined)}>
              {save.isPending ? 'Saving…' : 'Save avatar'}
            </Button>
            {changed && (
              <Button
                type="button"
                variant="ghost"
                onClick={() => {
                  setMode(current.kind);
                  setCharacter(current.character);
                  setPicked(null);
                  setPickError(null);
                }}
              >
                Undo
              </Button>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
