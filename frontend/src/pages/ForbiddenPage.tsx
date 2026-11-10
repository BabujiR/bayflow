import { Link } from 'react-router';

import { Button } from '@/components/ui/button';

export function ForbiddenPage() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-16 text-center">
      <p className="text-5xl font-bold text-primary">403</p>
      <h1 className="text-xl font-semibold">Not permitted</h1>
      <p className="text-muted-foreground">Your account doesn't have access to this page.</p>
      <Button asChild variant="outline">
        <Link to="/">Back to home</Link>
      </Button>
    </div>
  );
}
