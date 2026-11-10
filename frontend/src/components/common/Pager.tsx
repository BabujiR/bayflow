import { Button } from '@/components/ui/button';

export function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-end gap-3 text-sm">
      <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span className="text-muted-foreground">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next
      </Button>
    </nav>
  );
}
