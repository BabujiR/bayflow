import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

/** Placeholder for screens that arrive in milestone 7. */
export function ComingSoonPage({ title }: { title: string }) {
  return (
    <div className="grid gap-6">
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
      <Card>
        <CardHeader>
          <CardTitle>Coming in milestone 7</CardTitle>
          <CardDescription>
            Navigation, sign-in and permissions for this screen are in place; the screen itself is built next.
          </CardDescription>
        </CardHeader>
      </Card>
    </div>
  );
}
