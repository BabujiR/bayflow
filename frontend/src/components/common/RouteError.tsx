import { isRouteErrorResponse, useRouteError, type RouteObject } from 'react-router';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

/**
 * Shown instead of a page that failed to render (a bug, or an app update that removed a file
 * the page still needs), so nobody sees a blank screen or a stack trace. The error goes to the
 * browser console for support.
 */
export function RouteError() {
  const error = useRouteError();
  const notFound = isRouteErrorResponse(error) && error.status === 404;
  if (!notFound) {
    console.error('Page failed to render', error);
  }

  return (
    <div className="grid min-h-svh place-items-center bg-background p-4">
      <Card className="w-full max-w-md" role="alert">
        <CardHeader>
          <CardTitle>{notFound ? 'Page not found' : 'Something went wrong'}</CardTitle>
          <CardDescription>
            {notFound
              ? "This page doesn't exist. Check the address, or go back to the start."
              : 'This page ran into a problem. Reloading usually fixes it; your saved work is safe.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          {!notFound && <Button onClick={() => window.location.reload()}>Reload the page</Button>}
          <Button variant="outline" asChild>
            <a href="/">Go to the start</a>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}

/** The routes under one pathless parent whose error page catches any of them failing. */
export function withErrorPage(children: RouteObject[]): RouteObject[] {
  return [{ errorElement: <RouteError />, children }];
}
