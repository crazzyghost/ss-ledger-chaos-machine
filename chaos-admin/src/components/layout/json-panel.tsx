import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export function JsonPanel({ title, description, value }: { title: string; description: string; value: unknown }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {/* whitespace-pre-wrap keeps the JSON indentation/newlines while wrapping long values, and
            break-words breaks otherwise-unbreakable tokens (UUIDs, refs) so the payload fits the
            modal width instead of forcing horizontal scroll. */}
        <pre className="overflow-x-auto whitespace-pre-wrap break-words rounded-md bg-slate-950 px-4 py-3.5 text-xs leading-relaxed text-slate-200">
          {JSON.stringify(value, null, 2)}
        </pre>
      </CardContent>
    </Card>
  );
}