import { useCallback } from "react";
import { useSearchParams } from "react-router-dom";

/**
 * Tab selection persisted in the URL query string so it survives navigation. Because the active tab
 * lives in the URL, pressing the browser Back button after navigating away (e.g. opening a detail
 * page) returns to the exact tab that was selected.
 *
 * Selecting a tab replaces the current history entry (rather than pushing a new one) so the Back
 * button steps to the previous page, not through each tab change. The default tab is stored as the
 * absence of the param, keeping URLs clean.
 *
 * @param key the query-param name for this tab group (unique per page when multiple groups exist)
 * @param defaultValue the tab shown when the param is absent
 * @returns a `[value, setValue]` pair to wire into a controlled `<Tabs>`
 */
export function usePersistedTabs(
  key: string,
  defaultValue: string
): [string, (next: string) => void] {
  const [searchParams, setSearchParams] = useSearchParams();
  const value = searchParams.get(key) ?? defaultValue;

  const setValue = useCallback(
    (next: string) => {
      setSearchParams(
        prev => {
          const params = new URLSearchParams(prev);
          if (next === defaultValue) {
            params.delete(key);
          } else {
            params.set(key, next);
          }
          return params;
        },
        { replace: true }
      );
    },
    [key, defaultValue, setSearchParams]
  );

  return [value, setValue];
}
