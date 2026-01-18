"use client";

import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Toaster, toast } from "sonner";
import { ingestBillsAction, getBillsAction, clearBillsAction } from "@/app/actions/ingest-bills";
import type { Bill } from "@/lib/congress-service";

type IngestStatus = "idle" | "loading" | "success" | "error";

export default function AdminPage() {
  const [status, setStatus] = useState<IngestStatus>("idle");
  const [bills, setBills] = useState<Bill[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Load bills on mount
  useEffect(() => {
    loadBills();
  }, []);

  async function loadBills() {
    setIsLoading(true);
    try {
      const data = await getBillsAction();
      setBills(data);
    } catch {
      toast.error("Failed to load bills");
    } finally {
      setIsLoading(false);
    }
  }

  async function handleIngest() {
    setStatus("loading");
    try {
      const result = await ingestBillsAction();

      if (result.success && result.bill) {
        setStatus("success");
        toast.success("Bill ingested.", {
          description: `Added bill #${result.bill.id}: ${result.bill.title.slice(0, 50)}...`,
        });
        await loadBills();
      } else {
        setStatus("error");
        toast.error("Ingestion failed.", {
          description: result.error || "Check API settings.",
        });
      }
    } catch {
      setStatus("error");
      toast.error("Ingestion failed. Check API settings.");
    }
  }

  async function handleClear() {
    try {
      const result = await clearBillsAction();
      if (result.success) {
        toast.success("Bills cache cleared.");
        await loadBills();
      } else {
        toast.error("Failed to clear bills cache.");
      }
    } catch {
      toast.error("Failed to clear bills cache.");
    }
  }

  return (
    <div className="min-h-screen bg-stone-50 p-8">
      <Toaster
        position="top-right"
        toastOptions={{
          style: {
            background: "#1C1917",
            color: "#FFFFFF",
          },
        }}
      />

      <div className="max-w-4xl mx-auto space-y-8">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-bold text-stone-900">Admin Console</h1>
          <p className="text-stone-600 mt-2">
            Manage bill ingestion and view cached data.
          </p>
        </div>

        {/* Actions Card */}
        <Card className="border-stone-200">
          <CardHeader>
            <CardTitle className="text-stone-900">Bill Ingestion</CardTitle>
            <CardDescription className="text-stone-500">
              Fetch bills from Congress.gov API (Nov 1, 2025 onwards) and extract their text content.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex gap-4">
            <Button
              onClick={handleIngest}
              disabled={status === "loading"}
              className="bg-stone-900 text-white hover:bg-stone-800"
            >
              {status === "loading" ? "Ingesting..." : "Ingest Bill"}
            </Button>
            <Button
              onClick={handleClear}
              variant="outline"
              className="border-stone-200 text-stone-900 hover:bg-stone-100"
            >
              Clear Cache
            </Button>
          </CardContent>
        </Card>

        {/* Bills List */}
        <Card className="border-stone-200">
          <CardHeader>
            <CardTitle className="text-stone-900">Cached Bills</CardTitle>
            <CardDescription className="text-stone-500">
              {bills.length} bill{bills.length !== 1 ? "s" : ""} in cache
            </CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="text-center py-8 text-stone-500">
                Loading bills...
              </div>
            ) : bills.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-stone-600 mb-4">No bills ingested yet.</p>
                <Button
                  onClick={handleIngest}
                  disabled={status === "loading"}
                  className="bg-stone-900 text-white hover:bg-stone-800"
                >
                  Ingest Bill
                </Button>
              </div>
            ) : (
              <div className="space-y-4">
                {bills.map((bill) => (
                  <div
                    key={bill.id}
                    className="border border-stone-200 rounded-sm p-4 bg-white"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="font-mono text-sm text-stone-500 bg-stone-100 px-2 py-0.5 rounded">
                            ID: {bill.id}
                          </span>
                        </div>
                        <h3 className="font-medium text-stone-900">
                          {bill.title}
                        </h3>
                      </div>
                    </div>
                    <details className="mt-3">
                      <summary className="text-sm text-stone-600 cursor-pointer hover:text-stone-900">
                        View extracted text ({bill.rawText.length.toLocaleString()} chars)
                      </summary>
                      <pre className="mt-2 p-3 bg-stone-100 rounded-sm text-xs text-stone-700 overflow-auto max-h-48 whitespace-pre-wrap">
                        {bill.rawText.slice(0, 2000)}
                        {bill.rawText.length > 2000 && "..."}
                      </pre>
                    </details>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
