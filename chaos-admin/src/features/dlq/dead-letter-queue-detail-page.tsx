import { JsonPanel } from "@/components/layout/json-panel";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { DetailPanelSkeleton, InlineNotice, StatePanel } from "@/components/layout/state-panel";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/features/auth/session-provider";
import { getDeadLetter, type DeadLetterRecordResponse } from "@/lib/api";
import { formatDate } from "@/lib/utils";
import { usePersistedTabs } from "@/lib/use-persisted-tabs";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import type { ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";

// max-attempts=4 (3 retries), exponential 1s × 2 (ADR-029) — surfaced so "retry info" is meaningful
// even though a per-attempt timeline isn't available.
const RETRY_POLICY_NOTE = "Policy: max-attempts 4 (3 retries), exponential backoff 1s × 2";

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

function classificationVariant(classification: string | null): BadgeVariant {
  switch (classification?.toUpperCase()) {
    case "DESERIALIZATION":
      return "destructive";
    case "PROCESSING":
      return "warning";
    case "VERSION_RESOLUTION":
      return "neutral";
    default:
      return "secondary";
  }
}

function parseMaybe(json: string | null): unknown {
  if (!json) return null;
  try {
    return JSON.parse(json);
  } catch {
    return json; // unparseable → show the raw string verbatim
  }
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 break-all text-xs">{children}</dd>
    </div>
  );
}

/**
 * Tabbed detail for a single dead letter: <strong>Overview</strong> answers "what failed and why",
 * <strong>Message</strong> answers "what exactly was sent". A {@code DESERIALIZATION}-class dead
 * letter has no parseable original payload; the Message tab falls back to the raw DLT record.
 */
export function DeadLetterQueueDetailPage() {
  const { token } = useSession();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [tab, setTab] = usePersistedTabs("tab", "overview");

  const query = useQuery({
    queryKey: ["dlq", "detail", id],
    queryFn: () => getDeadLetter(token!, id!),
    enabled: Boolean(id)
  });

  const record: DeadLetterRecordResponse | undefined = query.data;
  const title = record?.originalTopic ?? id ?? "Dead letter";

  const backButton = (
    <Button variant="ghost" size="sm" onClick={() => navigate("/chaos/dlq")}>
      <ArrowLeft className="mr-1.5 h-4 w-4" />
      Back to queue
    </Button>
  );

  return (
    <Page>
      <PageHeader
        title={title}
        description={record ? `Dead letter ${record.id}` : "Loading…"}
        leadingActions={backButton}
      />
      <PageContent>
        {query.isLoading ? (
          <DetailPanelSkeleton includeSidebar={false} />
        ) : query.error ? (
          <StatePanel
            title={query.error instanceof Error && /404|not found/i.test(query.error.message)
              ? "Dead letter not found"
              : "Failed to load dead letter"}
            description={getErrorMessage(query.error)}
            tone="danger"
            icon="error"
            action={<Button onClick={() => void query.refetch()}>Retry</Button>}
            secondaryAction={
              <Button variant="ghost" onClick={() => navigate("/chaos/dlq")}>
                Back to queue
              </Button>
            }
          />
        ) : !record ? (
          <StatePanel
            title="Dead letter not found"
            description="It may have been pruned or never existed."
            action={<Button onClick={() => navigate("/chaos/dlq")}>Back to queue</Button>}
          />
        ) : (
          <Tabs value={tab} defaultValue="overview" onValueChange={setTab}>
            <TabsList>
              <TabsTrigger value="overview">Overview</TabsTrigger>
              <TabsTrigger value="message">Message</TabsTrigger>
            </TabsList>

            <TabsContent value="overview" className="pt-5">
              <Card>
                <CardHeader>
                  <CardTitle>What failed and why</CardTitle>
                </CardHeader>
                <CardContent>
                  <dl className="grid grid-cols-2 gap-x-6 gap-y-4 md:grid-cols-3">
                    <Field label="Domain">
                      <Badge variant="neutral">{record.domain}</Badge>
                    </Field>
                    <Field label="Original Topic">
                      <span className="font-mono">{record.originalTopic}</span>
                    </Field>
                    <Field label="Classification">
                      <Badge variant={classificationVariant(record.failureClassification)}>
                        {record.failureClassification ?? "—"}
                      </Badge>
                    </Field>
                    <Field label="Error code">
                      <span className="font-mono">{record.errorType ?? "—"}</span>
                    </Field>
                    <div className="md:col-span-2">
                      <Field label="Error reason">{record.errorMessage ?? "—"}</Field>
                    </div>
                    <Field label="Retries">
                      {record.retryCount ?? "—"}
                      <p className="mt-0.5 text-[10px] text-muted-foreground">{RETRY_POLICY_NOTE}</p>
                    </Field>
                    <Field label="Dead-lettered at">{formatDate(record.deadLetteredAt)}</Field>
                    <Field label="Received at">{formatDate(record.receivedAt)}</Field>
                    <Field label="Transaction ID">
                      <span className="font-mono">{record.transactionId ?? "—"}</span>
                    </Field>
                    <Field label="Transaction type">{record.transactionType ?? "—"}</Field>
                    <Field label="Event type">
                      <span className="font-mono">{record.eventType ?? "—"}</span>
                    </Field>
                    <Field label="DLT topic">
                      <span className="font-mono">{record.dltTopic}</span>
                    </Field>
                    <Field label="Original coordinates">
                      <span className="font-mono">
                        p{record.originalPartition ?? "—"} / o{record.originalOffset ?? "—"}
                      </span>
                      {record.originalKey ? (
                        <span className="ml-1 text-muted-foreground">key {record.originalKey}</span>
                      ) : null}
                    </Field>
                  </dl>
                </CardContent>
              </Card>
            </TabsContent>

            <TabsContent value="message" className="space-y-4 pt-5">
              {record.originalPayloadJson ? (
                <JsonPanel
                  title="Original payload"
                  description="The event the chaos machine published that the ledger rejected."
                  value={parseMaybe(record.originalPayloadJson)}
                />
              ) : record.rawDltJson ? (
                <>
                  <InlineNotice
                    tone="warning"
                    title="Original payload unparseable"
                    description="This is a DESERIALIZATION-class dead letter — the ledger could not parse the original payload, so the raw dead-letter record is shown instead."
                  />
                  <JsonPanel
                    title="Raw dead-letter record"
                    description="The full DeadLetterTopicRecord as received."
                    value={parseMaybe(record.rawDltJson)}
                  />
                </>
              ) : (
                <StatePanel
                  title="No payload captured"
                  description="Neither the original payload nor the raw DLT record was available for this dead letter."
                />
              )}
            </TabsContent>
          </Tabs>
        )}
      </PageContent>
    </Page>
  );
}
