"use client";

import { Toaster } from "sonner";
import { ContractHeader } from "@/components/contract/ContractHeader";
import { ContractViewer } from "@/components/contract/ContractViewer";
import { ObservabilityPanel } from "@/components/contract/ObservabilityPanel";
import { StakeholdersSection } from "@/components/contract/StakeholdersSection";

export default function ContractPage() {
  return (
    <div
      style={{
        minHeight: "100vh",
        background: "#f6f8fb",
        fontFamily: "'Space Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace",
      }}
    >
      <Toaster
        position="top-right"
        toastOptions={{
          style: {
            background: "#101622",
            color: "#ffffff",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            border: "1px solid #d6dee9",
            borderRadius: "8px",
          },
        }}
      />

      <div
        style={{
          maxWidth: "1200px",
          margin: "0 auto",
          padding: "12px 16px 52px",
          display: "flex",
          flexDirection: "column",
          gap: "14px",
        }}
      >
        {/* Header */}
        <ContractHeader />

        {/* Main Content Grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 360px",
            gap: "14px",
          }}
        >
          {/* Contract View - Takes more space */}
          <ContractViewer />

          {/* Observability Panel - Sidebar */}
          <ObservabilityPanel />
        </div>

        {/* Stakeholders Section - Full width */}
        <StakeholdersSection />
      </div>
    </div>
  );
}
