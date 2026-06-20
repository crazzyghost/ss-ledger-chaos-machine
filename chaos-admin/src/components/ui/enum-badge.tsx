import { Badge } from "@/components/ui/badge";
import { formatEnumValue, getEnumBadgeVariant } from "@/lib/utils";

type EnumBadgeProps = {
  value?: string | null;
  label?: string;
};

export function EnumBadge({ value, label }: EnumBadgeProps) {
  if (!value) {
    return <span className="text-xs text-muted-foreground">-</span>;
  }

  return (
    <Badge variant={getEnumBadgeVariant(value)} className="font-medium">
      {label ?? formatEnumValue(value)}
    </Badge>
  );
}
