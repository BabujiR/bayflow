import * as DialogPrimitive from '@radix-ui/react-dialog';
import { useEffect, useState, useSyncExternalStore } from 'react';
import { Link, NavLink, useLocation } from 'react-router';

import type { Role } from '@/api/types';
import { CloseIcon, CollapseIcon, ExpandIcon, MenuIcon } from '@/components/ui/icons';
import { WorkshopLogo } from '@/features/branding/WorkshopLogo';
import { navGroupsFor } from '@/lib/navigation';
import { cn } from '@/lib/utils';

// ---- collapsed or not: remembered in this browser ------------------------------------------

const COLLAPSED_KEY = 'bayflow.sidebar.collapsed';
const listeners = new Set<() => void>();

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === '1';
  } catch {
    return false;
  }
}

let collapsed = readCollapsed();

function setCollapsed(next: boolean) {
  collapsed = next;
  try {
    localStorage.setItem(COLLAPSED_KEY, next ? '1' : '0');
  } catch {
    // Private windows may refuse storage; the choice still holds until the page reloads.
  }
  listeners.forEach((listener) => listener());
}

function useCollapsed(): boolean {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => collapsed,
    () => false,
  );
}

// ---- the links -----------------------------------------------------------------------------

/**
 * The user's links in sections (Workshop, Insights, Admin). Collapsed: icons only, each with its
 * name as a tooltip and for screen readers.
 */
function SidebarLinks({
  roles,
  label,
  compact = false,
  onNavigate,
}: {
  roles: Role[];
  label: string;
  compact?: boolean;
  onNavigate?: () => void;
}) {
  const sections = navGroupsFor(roles);
  return (
    <nav aria-label={label} className="grid gap-5">
      {sections.map((section, index) => (
        <div key={section.group} role="group" aria-label={section.label} className="grid gap-0.5">
          {compact ? (
            index > 0 && <hr className="mx-2 mb-2 border-border" />
          ) : (
            <p
              aria-hidden
              className="px-3 pb-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground"
            >
              {section.label}
            </p>
          )}
          {section.items.map(({ to, label: name, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              title={compact ? name : undefined}
              onClick={onNavigate}
              className={({ isActive }) =>
                cn(
                  'flex h-10 items-center gap-3 rounded-md text-sm font-medium transition-colors',
                  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
                  compact ? 'justify-center px-0' : 'px-3',
                  isActive
                    ? 'bg-primary/10 text-primary'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                )
              }
            >
              <Icon className="size-5 shrink-0" />
              <span className={cn('truncate', compact && 'sr-only')}>{name}</span>
            </NavLink>
          ))}
        </div>
      ))}
    </nav>
  );
}

/** The workshop's logo (or Ramplight's) and, without a logo of its own, the workshop's name. */
function SidebarBrand({
  homeLabel,
  workshopName,
  showName,
  compact = false,
  nameTestId,
}: {
  homeLabel: string;
  workshopName?: string;
  showName: boolean;
  compact?: boolean;
  nameTestId?: string;
}) {
  return (
    <div className={cn('grid min-w-0 gap-1', compact && 'justify-items-center')}>
      <Link to="/" aria-label={homeLabel} className="w-fit max-w-full rounded-md focus-visible:outline-2">
        <WorkshopLogo size="sidebar" compact={compact} />
      </Link>
      {showName && workshopName && !compact && (
        <span
          className="truncate text-sm font-semibold text-muted-foreground"
          data-testid={nameTestId}
          title={workshopName}
        >
          {workshopName}
        </span>
      )}
    </div>
  );
}

// ---- wide screens: the sidebar -------------------------------------------------------------

/** Staff navigation on wide screens: always shown, collapsible to icons. */
export function Sidebar({
  roles,
  homeLabel,
  workshopName,
  showName,
}: {
  roles: Role[];
  homeLabel: string;
  workshopName?: string;
  showName: boolean;
}) {
  const compact = useCollapsed();
  return (
    <aside
      data-testid="sidebar"
      data-collapsed={compact}
      className={cn(
        'sticky top-0 hidden h-svh shrink-0 flex-col border-r bg-card transition-[width] duration-200 lg:flex',
        compact ? 'w-16' : 'w-60',
      )}
    >
      <div
        className={cn('flex min-h-16 items-center border-b py-3', compact ? 'justify-center px-2' : 'px-4')}
      >
        <SidebarBrand
          homeLabel={homeLabel}
          workshopName={workshopName}
          showName={showName}
          compact={compact}
          nameTestId="workshop-name"
        />
      </div>
      <div className={cn('flex-1 overflow-y-auto py-4', compact ? 'px-2' : 'px-3')}>
        <SidebarLinks roles={roles} label="Main" compact={compact} />
      </div>
      <div className={cn('border-t py-2', compact ? 'px-2' : 'px-3')}>
        <button
          type="button"
          onClick={() => setCollapsed(!compact)}
          aria-label={compact ? 'Expand sidebar' : 'Collapse sidebar'}
          title={compact ? 'Expand sidebar' : undefined}
          className={cn(
            'flex h-9 w-full items-center gap-3 rounded-md text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
            compact ? 'justify-center' : 'px-3',
          )}
        >
          {compact ? <ExpandIcon className="size-4" /> : <CollapseIcon className="size-4" />}
          {!compact && <span>Collapse</span>}
        </button>
      </div>
    </aside>
  );
}

// ---- small screens: the same links in a drawer ---------------------------------------------

/** The ☰ button and the slide-over menu it opens (below the lg breakpoint). */
export function MobileMenu({
  roles,
  homeLabel,
  workshopName,
  showName,
}: {
  roles: Role[];
  homeLabel: string;
  workshopName?: string;
  showName: boolean;
}) {
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();

  // Going to another page (a link, back, a notification) closes the menu.
  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  return (
    <DialogPrimitive.Root open={open} onOpenChange={setOpen}>
      <DialogPrimitive.Trigger
        aria-label="Open menu"
        className="inline-flex size-10 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground lg:hidden"
      >
        <MenuIcon className="size-5" />
      </DialogPrimitive.Trigger>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-[1px] lg:hidden" />
        <DialogPrimitive.Content
          aria-describedby={undefined}
          className="fixed inset-y-0 left-0 z-50 flex w-72 max-w-[85vw] flex-col border-r bg-card shadow-xl lg:hidden"
        >
          <DialogPrimitive.Title className="sr-only">Menu</DialogPrimitive.Title>
          <div className="flex min-h-16 items-center justify-between gap-2 border-b px-4 py-3">
            <SidebarBrand homeLabel={homeLabel} workshopName={workshopName} showName={showName} />
            <DialogPrimitive.Close
              aria-label="Close menu"
              className="inline-flex size-9 shrink-0 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <CloseIcon className="size-5" />
            </DialogPrimitive.Close>
          </div>
          <div className="flex-1 overflow-y-auto px-3 py-4">
            <SidebarLinks roles={roles} label="Main (menu)" onNavigate={() => setOpen(false)} />
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}
