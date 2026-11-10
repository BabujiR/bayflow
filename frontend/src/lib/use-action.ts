import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { toast } from 'sonner';

import { errorMessage } from '@/api/errors';

interface ActionOptions<TResult> {
  /** Toast shown on success. */
  success?: string | ((result: TResult) => string);
  /** Queries to refetch afterwards (prefix match). */
  invalidate?: QueryKey[];
  onSuccess?: (result: TResult) => void;
  /** Called instead of the error toast, e.g. to show errors on form fields. Return true if handled. */
  onError?: (error: unknown) => boolean | void;
}

/** A mutation with the usual Bayflow behaviour: refetch, toast on success, toast the error. */
export function useAction<TVariables, TResult>(
  action: (variables: TVariables) => Promise<TResult>,
  options: ActionOptions<TResult> = {},
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: action,
    onSuccess: async (result) => {
      await Promise.all((options.invalidate ?? []).map((queryKey) => queryClient.invalidateQueries({ queryKey })));
      if (options.success) {
        toast.success(typeof options.success === 'function' ? options.success(result) : options.success);
      }
      options.onSuccess?.(result);
    },
    onError: (error) => {
      if (!options.onError?.(error)) {
        toast.error(errorMessage(error));
      }
    },
  });
}
