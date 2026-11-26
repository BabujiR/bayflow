import { useNavigate } from 'react-router';
import { toast } from 'sonner';

import { useAuth } from '@/auth/auth-context';
import { ROLE_LABELS } from '@/auth/roles';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { KeyIcon, LogOutIcon, UserIcon } from '@/components/ui/icons';

export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) {
    return null;
  }
  const roles = user.roles.map((role) => ROLE_LABELS[role]).join(', ');

  const signOut = async () => {
    try {
      await logout();
    } catch {
      // The session is cleared locally even if the server call fails.
    }
    toast.success('Signed out');
    navigate('/login', { replace: true });
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="gap-2 px-2" aria-label={`Account menu for ${user.username}`}>
          <UserAvatar name={user.username} avatar={user.avatar} />
          <span className="hidden text-left sm:block">
            <span className="block text-sm font-medium leading-tight">{user.username}</span>
            <span className="block text-xs text-muted-foreground">{roles}</span>
          </span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          <span className="block font-medium">{user.username}</span>
          <span className="block text-xs text-muted-foreground">{user.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => navigate('/profile')}>
          <UserIcon className="size-4" /> My profile
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => navigate('/change-password')}>
          <KeyIcon className="size-4" /> Change password
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => void signOut()}>
          <LogOutIcon className="size-4" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
