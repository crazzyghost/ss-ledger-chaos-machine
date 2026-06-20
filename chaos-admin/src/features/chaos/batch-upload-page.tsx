import { InlineNotice } from "@/components/layout/state-panel";
import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel } from "@/components/layout/state-panel";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { useSession } from "@/features/auth/session-provider";
import { getFlowCatalog, startBatch } from "@/lib/api";
import { formatEnumValue } from "@/lib/utils";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ArrowLeft, Upload } from "lucide-react";
import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

const MAX_FILE_SIZE_MB = 10;

function getErrorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

export function BatchUploadPage() {
  const { token } = useSession();
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [flowType, setFlowType] = useState("");
  const [maxRatePerSecond, setMaxRatePerSecond] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const catalogQuery = useQuery({
    queryKey: ["flow-catalog"],
    queryFn: () => getFlowCatalog(token!)
  });

  const catalog = catalogQuery.data ?? [];
  const flowTypeOptions = catalog.map(c => ({ value: c.flowType, label: formatEnumValue(c.flowType) }));

  const selectedCatalog = catalog.find(c => c.flowType === flowType) ?? null;

  const mutation = useMutation({
    mutationFn: () => {
      const fd = new FormData();
      fd.append("file", selectedFile!);
      fd.append("flowType", flowType);
      if (maxRatePerSecond.trim()) {
        fd.append("maxRatePerSecond", maxRatePerSecond.trim());
      }
      return startBatch(token!, fd);
    },
    onSuccess: batch => {
      navigate(`/chaos/batches/${batch.id}`);
    },
    onError: err => setFormError(getErrorMessage(err))
  });

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    if (file && file.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
      setFormError(`File exceeds ${MAX_FILE_SIZE_MB} MB limit.`);
      setSelectedFile(null);
      return;
    }
    setFormError(null);
    setSelectedFile(file);
  }

  function handleSubmit() {
    setFormError(null);
    if (!selectedFile) {
      setFormError("Please select a CSV file.");
      return;
    }
    if (!flowType) {
      setFormError("Please select a flow type.");
      return;
    }
    mutation.mutate();
  }

  function downloadTemplate() {
    if (!selectedCatalog) return;
    const cols = selectedCatalog.csvColumns.join(",");
    const blob = new Blob([cols + "\n"], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${flowType.toLowerCase()}_template.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <Page>
      <PageHeader
        title="CSV Batch Upload"
        description="Upload a CSV file to run a batch of flow events against the configured Kafka cluster."
        leadingActions={
          <Button variant="ghost" size="sm" onClick={() => navigate("/chaos/batches")}>
            <ArrowLeft className="mr-1.5 h-4 w-4" />
            All Batches
          </Button>
        }
      />
      <PageContent className="max-w-2xl">
        <Card>
          <CardHeader>
            <CardTitle>Batch Configuration</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {/* Flow type */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Flow Type</label>
              {catalogQuery.isLoading ? (
                <div className="h-9 animate-pulse rounded-md bg-muted/40" />
              ) : (
                <Select
                  value={flowType as string & { __brand: "flow" }}
                  onChange={v => setFlowType(v)}
                  options={flowTypeOptions as { value: string & { __brand: "flow" }; label: string }[]}
                  placeholder="Select a flow…"
                  searchable
                  searchPlaceholder="Search flows…"
                />
              )}
              {selectedCatalog && (
                <div className="space-y-1">
                  <Button variant="ghost" size="sm" onClick={downloadTemplate}>
                    Download CSV template for {formatEnumValue(flowType)}
                  </Button>
                  <p className="text-[10px] text-muted-foreground">
                    Current backend batches accept one flow type per upload.
                  </p>
                </div>
              )}
            </div>

            {/* CSV file */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium">CSV File (max {MAX_FILE_SIZE_MB} MB)</label>
              <div
                className="flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-border bg-muted/20 px-6 py-8 transition-colors hover:bg-muted/40"
                onClick={() => fileInputRef.current?.click()}
                onKeyDown={e => {
                  if (e.key === "Enter" || e.key === " ") fileInputRef.current?.click();
                }}
                role="button"
                tabIndex={0}
              >
                <Upload className="mb-2 h-8 w-8 text-muted-foreground" />
                {selectedFile ? (
                  <p className="text-xs font-medium text-foreground">{selectedFile.name}</p>
                ) : (
                  <p className="text-xs text-muted-foreground">
                    Click to select a CSV file
                  </p>
                )}
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".csv,text/csv"
                  className="hidden"
                  onChange={handleFileChange}
                />
              </div>
            </div>

            {/* Rate limit */}
            <div className="space-y-1.5">
              <label className="text-xs font-medium">Max Rate per Second (optional)</label>
              <Input
                type="number"
                min={1}
                max={1000}
                value={maxRatePerSecond}
                onChange={e => setMaxRatePerSecond(e.target.value)}
                placeholder="Leave blank for default"
              />
            </div>

            {formError && <InlineNotice description={formError} tone="danger" />}

            <Button
              onClick={handleSubmit}
              disabled={mutation.isPending || !selectedFile || !flowType}
              className="w-full"
            >
              {mutation.isPending ? "Uploading…" : "Start Batch Run"}
            </Button>
          </CardContent>
        </Card>
      </PageContent>
    </Page>
  );
}
