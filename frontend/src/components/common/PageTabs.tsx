import { cn } from '@/lib/utils';

/**
 * A row of tabs under a page header (Reports, Settings). The panel the selected tab shows is
 * rendered by the page, with `role="tabpanel"` and `aria-labelledby={tabId(idPrefix, selected)}`.
 */
export function PageTabs<T extends string>({
  label,
  idPrefix,
  tabs,
  selected,
  onSelect,
}: {
  label: string;
  idPrefix: string;
  tabs: Record<T, string>;
  selected: T;
  onSelect: (tab: T) => void;
}) {
  const keys = Object.keys(tabs) as T[];
  return (
    <div role="tablist" aria-label={label} className="mb-6 flex flex-wrap gap-1 border-b">
      {keys.map((t) => (
        <button
          key={t}
          type="button"
          role="tab"
          id={tabId(idPrefix, t)}
          aria-selected={selected === t}
          aria-controls={`${idPrefix}-panel`}
          className={cn(
            '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
            selected === t
              ? 'border-primary text-foreground'
              : 'border-transparent text-muted-foreground hover:text-foreground',
          )}
          onClick={() => onSelect(t)}
        >
          {tabs[t]}
        </button>
      ))}
    </div>
  );
}

export const tabId = (idPrefix: string, tab: string) => `${idPrefix}-tab-${tab}`;
