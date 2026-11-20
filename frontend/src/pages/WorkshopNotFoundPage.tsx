import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { PRODUCT_NAME } from '@/lib/brand';
import { BASE_DOMAIN, publicSiteUrl } from '@/lib/site';

/** Shown at an address that names no workshop, e.g. a mistyped acme.localhost. */
export function WorkshopNotFoundPage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Workshop not found</CardTitle>
        <CardDescription>
          There is no {PRODUCT_NAME} workshop at <strong>{window.location.host}</strong>. Check the address
          with your workshop, or set up a new one.
        </CardDescription>
      </CardHeader>
      <CardContent className="text-sm text-muted-foreground">
        Workshop addresses look like <code>your-workshop.{BASE_DOMAIN}</code>.
      </CardContent>
      <CardFooter>
        <Button asChild className="w-full">
          <a href={publicSiteUrl('/signup')}>Set up a workshop</a>
        </Button>
      </CardFooter>
    </Card>
  );
}
