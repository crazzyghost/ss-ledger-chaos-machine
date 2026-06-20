import { Page, PageContent, PageHeader } from "@/components/layout/page";
import { StatePanel } from "@/components/layout/state-panel";
import { Button } from "@/components/ui/button";
import { isRouteErrorResponse, useNavigate, useRouteError } from "react-router-dom";

function getRouteErrorDetails(error: unknown) {
  if (isRouteErrorResponse(error)) {
    return {
      title: error.status === 404 ? "Page not found" : `Request failed (${error.status})`,
      description:
        typeof error.data === "string"
          ? error.data
          : error.status === 404
            ? "The requested admin surface could not be found."
            : error.statusText || "The requested route failed to load.",
      tone: (error.status >= 500 ? "danger" : "warning") as "danger" | "warning"
    };
  }

  if (error instanceof Error) {
    return {
      title: "Unexpected application error",
      description: error.message || "The app hit an unexpected failure while rendering.",
      tone: "danger" as const
    };
  }

  return {
    title: "Unexpected application error",
    description: "The app hit an unexpected failure while rendering this screen.",
    tone: "danger" as const
  };
}

export function RouteErrorBoundary() {
  const error = useRouteError();
  const navigate = useNavigate();
  const details = getRouteErrorDetails(error);

  return (
    <Page>
      <PageHeader
        title="Something went wrong"
        description="This route failed to render, but the rest of the console is still available."
      />
      <PageContent className="max-w-3xl">
        <StatePanel
          title={details.title}
          description={details.description}
          tone={details.tone}
          icon="error"
          action={<Button onClick={() => window.location.reload()}>Reload page</Button>}
          secondaryAction={
            <Button variant="outline" onClick={() => navigate("/chaos/single-flow")}>
              Go to Single Flow
            </Button>
          }
        />
      </PageContent>
    </Page>
  );
}
