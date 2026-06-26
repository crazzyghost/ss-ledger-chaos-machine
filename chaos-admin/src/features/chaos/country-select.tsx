import { Select } from "@/components/ui/select";
import { listSupportedCountries } from "@/lib/api";
import { useQuery } from "@tanstack/react-query";

/**
 * Renders a {@code FieldKind.COUNTRY} descriptor as a searchable select whose options come from the
 * supported-countries table (Phase 010), keyed by ISO code. The current value stays selectable even
 * if it is not in the supported list (so a seeded default like {@code GH} always shows).
 */
export function CountrySelect({
  token,
  value,
  onChange
}: {
  token: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const query = useQuery({
    queryKey: ["supported-countries", "picker"],
    queryFn: () => listSupportedCountries(token, { perPage: 200 }),
    enabled: Boolean(token)
  });

  const options = (query.data?.items ?? [])
    .map(sc => sc.country)
    .filter((c): c is NonNullable<typeof c> => Boolean(c?.isoCode))
    .map(c => ({ value: c.isoCode, label: `${c.isoCode} · ${c.name}` }));

  const hasValue = options.some(o => o.value === value);
  const allOptions = value && !hasValue ? [{ value, label: value }, ...options] : options;

  return (
    <Select
      value={value}
      onChange={onChange}
      options={allOptions}
      placeholder={query.isLoading ? "Loading countries…" : "Select country…"}
      searchable
      searchPlaceholder="Search countries…"
      emptyText="No supported countries."
    />
  );
}
