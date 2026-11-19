import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { brandingApi } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import type { Branding, BrandingImageKind } from '@/api/types';
import { QueryState } from '@/components/common/QueryState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { PUBLIC_WORKSHOP_QUERY_KEY } from '@/features/workshop/use-public-workshop';
import { isoToLocalInput, localInputToIso, localInputToMillis, zoneLabel } from '@/lib/format';
import { useAction } from '@/lib/use-action';
import { cn } from '@/lib/utils';

import { colorWarning, foregroundFor, isHexColor } from './colors';

export const BRANDING_QUERY_KEY = ['branding'] as const;

/** Bayflow blue, offered as the starting point when a workshop picks its own colour. */
const BAYFLOW_BLUE = '#1d4ed8';
const BANNER_MAX = 200;
const IMAGE_TYPES = ['image/png', 'image/jpeg'];

const IMAGES: Record<BrandingImageKind, { title: string; hint: string; maxBytes: number }> = {
  logo: {
    title: 'Logo',
    hint: 'PNG or JPEG up to 1 MB. Shown in the header and on the sign-in page; a wide logo on a transparent background works best.',
    maxBytes: 1024 * 1024,
  },
  cover: {
    title: 'Sign-in cover photo',
    hint: 'PNG or JPEG up to 3 MB, landscape. Shown beside the sign-in form, e.g. a photo of your workshop.',
    maxBytes: 3 * 1024 * 1024,
  },
};

const brandingSchema = z
  .object({
    useOwnColor: z.boolean(),
    primaryColor: z.string().trim(),
    bannerText: z.string().trim().max(BANNER_MAX, `At most ${BANNER_MAX} characters`),
    bannerStyle: z.enum(['INFO', 'IMPORTANT']),
    bannerStartsAt: z.string(),
    bannerEndsAt: z.string(),
    showPoweredBy: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.useOwnColor && !isHexColor(values.primaryColor)) {
      ctx.addIssue({ code: 'custom', path: ['primaryColor'], message: 'A colour like #1d4ed8' });
    }
    const starts = localInputToMillis(values.bannerStartsAt);
    const ends = localInputToMillis(values.bannerEndsAt);
    if (values.bannerStartsAt && Number.isNaN(starts)) {
      ctx.addIssue({ code: 'custom', path: ['bannerStartsAt'], message: 'Enter a date and time' });
    }
    if (values.bannerEndsAt && Number.isNaN(ends)) {
      ctx.addIssue({ code: 'custom', path: ['bannerEndsAt'], message: 'Enter a date and time' });
    }
    if (!Number.isNaN(starts) && !Number.isNaN(ends) && ends <= starts) {
      ctx.addIssue({ code: 'custom', path: ['bannerEndsAt'], message: 'Must be after the start' });
    }
  });

type BrandingForm = z.infer<typeof brandingSchema>;

/** The workshop ADMIN's branding: logo, cover photo, colour, announcement and "Powered by Bayflow". */
export function BrandingSection() {
  const branding = useQuery({ queryKey: BRANDING_QUERY_KEY, queryFn: brandingApi.get });
  return (
    <section aria-labelledby="branding-heading" className="grid max-w-3xl gap-6">
      <div>
        <h2 id="branding-heading" className="text-xl font-semibold tracking-tight">
          Branding
        </h2>
        <p className="text-sm text-muted-foreground">
          How your workshop looks to customers at its address: your logo, colour, sign-in photo and
          announcements.
        </p>
      </div>
      <QueryState query={branding}>
        {(b) => (
          <>
            <Card>
              <CardHeader>
                <CardTitle>Images</CardTitle>
                <CardDescription>
                  Images are checked and saved again on the server; their metadata (such as the camera
                  location) is removed.
                </CardDescription>
              </CardHeader>
              <CardContent className="grid gap-6 sm:grid-cols-2">
                <ImageSetting kind="logo" url={b.logoUrl} />
                <ImageSetting kind="cover" url={b.coverUrl} />
              </CardContent>
            </Card>
            <BrandingDetails key={b.version} branding={b} />
          </>
        )}
      </QueryState>
    </section>
  );
}

function useBrandingSaved() {
  const queryClient = useQueryClient();
  return (saved: Branding) => {
    queryClient.setQueryData(BRANDING_QUERY_KEY, saved);
    // Logo, colour and banner on this page (and the sign-in page) follow at once.
    void queryClient.invalidateQueries({ queryKey: PUBLIC_WORKSHOP_QUERY_KEY });
  };
}

function ImageSetting({ kind, url }: { kind: BrandingImageKind; url: string | null }) {
  const { title, hint, maxBytes } = IMAGES[kind];
  const input = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const saved = useBrandingSaved();
  const onError = (error: unknown) => {
    if (error instanceof ApiError && error.isClientError) {
      setProblem(error.message);
      return true;
    }
    return false;
  };
  const upload = useAction(brandingApi.upload, { success: `${title} saved`, onSuccess: saved, onError });
  const remove = useAction(brandingApi.remove, { success: `${title} removed`, onSuccess: saved, onError });
  const busy = upload.isPending || remove.isPending;
  const inputId = `branding-${kind}`;

  const choose = (file: File | undefined) => {
    setProblem(null);
    if (!file) {
      return;
    }
    if (!IMAGE_TYPES.includes(file.type)) {
      setProblem('Choose a PNG or JPEG image.');
      return;
    }
    if (file.size > maxBytes) {
      setProblem(`The file is too large: at most ${maxBytes / (1024 * 1024)} MB.`);
      return;
    }
    upload.mutate({ kind, file });
  };

  return (
    <div className="grid content-start gap-3" data-testid={`branding-${kind}`}>
      <div className="text-sm font-medium">{title}</div>
      <div
        className={cn(
          'flex items-center justify-center overflow-hidden rounded-md border bg-muted/40',
          kind === 'logo' ? 'h-24' : 'h-32',
        )}
      >
        {url ? (
          <img
            src={url}
            alt={`Current ${title.toLowerCase()}`}
            className={kind === 'logo' ? 'max-h-20 max-w-[90%] object-contain' : 'size-full object-cover'}
          />
        ) : (
          <span className="text-sm text-muted-foreground">
            {kind === 'logo' ? 'Bayflow logo shown' : 'No cover photo'}
          </span>
        )}
      </div>
      <p className="text-xs text-muted-foreground">{hint}</p>
      <input
        ref={input}
        id={inputId}
        type="file"
        accept={IMAGE_TYPES.join(',')}
        className="sr-only"
        aria-label={`Upload ${title.toLowerCase()}`}
        onChange={(event) => {
          choose(event.target.files?.[0]);
          event.target.value = '';
        }}
      />
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={busy}
          onClick={() => input.current?.click()}
        >
          {url ? `Replace ${title.toLowerCase()}` : `Upload ${title.toLowerCase()}`}
        </Button>
        {url && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={busy}
            onClick={() => {
              setProblem(null);
              remove.mutate(kind);
            }}
          >
            Remove
          </Button>
        )}
      </div>
      {problem && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
    </div>
  );
}

function BrandingDetails({ branding }: { branding: Branding }) {
  const queryClient = useQueryClient();
  const saved = useBrandingSaved();
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    watch,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<BrandingForm>({
    resolver: zodResolver(brandingSchema),
    defaultValues: {
      useOwnColor: !!branding.primaryColor,
      primaryColor: branding.primaryColor ?? BAYFLOW_BLUE,
      bannerText: branding.bannerText ?? '',
      bannerStyle: branding.bannerStyle,
      bannerStartsAt: branding.bannerStartsAt ? isoToLocalInput(branding.bannerStartsAt) : '',
      bannerEndsAt: branding.bannerEndsAt ? isoToLocalInput(branding.bannerEndsAt) : '',
      showPoweredBy: branding.showPoweredBy,
    },
  });
  const save = useAction(brandingApi.update, {
    success: 'Branding saved',
    onSuccess: saved,
    onError: (error) => {
      if (error instanceof ApiError) {
        if (error.code === 'invalid-color') {
          setError('primaryColor', { message: error.message });
          return true;
        }
        if (error.code === 'invalid-banner') {
          setError('bannerText', { message: error.message });
          return true;
        }
        if (error.code === 'invalid-banner-window') {
          setError('bannerEndsAt', { message: error.message });
          return true;
        }
        if (error.code === 'concurrent-update') {
          void queryClient.invalidateQueries({ queryKey: BRANDING_QUERY_KEY });
        }
      }
      return false;
    },
  });

  const ownColor = watch('useOwnColor');
  const color = watch('primaryColor');
  const validColor = isHexColor(color) ? color.toLowerCase() : null;
  const warning = ownColor && validColor ? colorWarning(validColor) : null;
  const bannerText = watch('bannerText') ?? '';
  const bannerStyle = watch('bannerStyle');
  const zone = zoneLabel();

  return (
    <form
      noValidate
      aria-label="Branding"
      className="grid gap-6"
      onSubmit={handleSubmit(async (values) => {
        await save
          .mutateAsync({
            primaryColor: values.useOwnColor ? values.primaryColor.toLowerCase() : null,
            bannerText: values.bannerText || null,
            bannerStyle: values.bannerStyle,
            bannerStartsAt: values.bannerStartsAt ? localInputToIso(values.bannerStartsAt) : null,
            bannerEndsAt: values.bannerEndsAt ? localInputToIso(values.bannerEndsAt) : null,
            showPoweredBy: values.showPoweredBy,
            version: branding.version,
          })
          .catch(() => undefined);
      })}
    >
      <Card>
        <CardHeader>
          <CardTitle>Colour</CardTitle>
          <CardDescription>
            Used for buttons, links and highlights, in the light and the dark theme. Text on it turns white or
            black automatically, whichever reads better.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" className="size-4 accent-primary" {...register('useOwnColor')} />
            Use my workshop's colour
          </label>
          {ownColor && (
            <div className="grid gap-4 sm:grid-cols-[auto_1fr_auto] sm:items-end">
              <input
                type="color"
                aria-label="Pick a colour"
                className="h-10 w-14 cursor-pointer rounded-md border border-input bg-background p-1"
                value={validColor ?? BAYFLOW_BLUE}
                onChange={(event) =>
                  setValue('primaryColor', event.target.value, { shouldDirty: true, shouldValidate: true })
                }
              />
              <Field label="Colour code" error={errors.primaryColor?.message}>
                <Input maxLength={7} spellCheck={false} autoCapitalize="off" {...register('primaryColor')} />
              </Field>
              {validColor && (
                <span
                  className="inline-flex h-10 items-center rounded-md px-4 text-sm font-medium"
                  style={{ backgroundColor: validColor, color: foregroundFor(validColor) }}
                  data-testid="color-preview"
                >
                  Book a service
                </span>
              )}
            </div>
          )}
          {warning && (
            <p role="status" className="text-sm" data-testid="color-warning">
              <span className="font-medium">Hard to read: </span>
              <span className="text-muted-foreground">{warning}</span>
            </p>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Announcement</CardTitle>
          <CardDescription>
            A one-line message at the top of every page, e.g. holiday hours or a seasonal offer. Leave the
            dates empty to show it until you remove it. Times are in {zone || 'the workshop time zone'}.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <Field
              label="Message"
              error={errors.bannerText?.message}
              hint={`${bannerText.length}/${BANNER_MAX} characters. Plain text.`}
            >
              <Textarea rows={2} maxLength={BANNER_MAX} {...register('bannerText')} />
            </Field>
          </div>
          <Field label="Style" error={errors.bannerStyle?.message}>
            <Select {...register('bannerStyle')}>
              <option value="INFO">Information</option>
              <option value="IMPORTANT">Important</option>
            </Select>
          </Field>
          <div />
          <Field label="Show from" error={errors.bannerStartsAt?.message}>
            <Input type="datetime-local" {...register('bannerStartsAt')} />
          </Field>
          <Field label="Show until" error={errors.bannerEndsAt?.message}>
            <Input type="datetime-local" {...register('bannerEndsAt')} />
          </Field>
          {bannerText.trim() && (
            <div className="sm:col-span-2">
              <div className="mb-1 text-xs text-muted-foreground">
                Preview{branding.bannerLive ? ' (live now)' : ''}
              </div>
              <div
                className={cn(
                  'rounded-md border px-4 py-2.5 text-center text-sm',
                  bannerStyle === 'IMPORTANT'
                    ? 'border-warning/50 bg-warning/20 font-medium'
                    : 'border-primary/20 bg-primary/10',
                )}
              >
                {bannerText}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Powered by Bayflow</CardTitle>
          <CardDescription>A small line at the bottom of the sign-in page and the app.</CardDescription>
        </CardHeader>
        <CardContent>
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" className="size-4 accent-primary" {...register('showPoweredBy')} />
            Show "Powered by Bayflow"
          </label>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting || !isDirty}>
          Save branding
        </Button>
      </div>
    </form>
  );
}
