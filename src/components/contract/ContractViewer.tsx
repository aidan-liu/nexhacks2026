"use client";

import { useState } from "react";
import { Code, FileText } from "lucide-react";
import { CodeView } from "./CodeView";
import { MarkdownView } from "./MarkdownView";

export function ContractViewer() {
  const [activeTab, setActiveTab] = useState<"summary" | "code">("summary");

  return (
    <div
      style={{
        background: "#ffffff",
        border: "1px solid #e1e7f0",
        borderRadius: "10px",
        padding: "8px",
        height: "100%",
      }}
    >
      {/* Header */}
      <h3
        style={{
          margin: "0 0 8px 0",
          fontFamily: "'Space Mono', monospace",
          fontSize: "12px",
          letterSpacing: "0.6px",
          textTransform: "uppercase",
          color: "#667085",
        }}
      >
        Contract View
      </h3>

      {/* Tab Buttons */}
      <div
        style={{
          display: "flex",
          gap: "6px",
          marginBottom: "12px",
        }}
      >
        <button
          onClick={() => setActiveTab("summary")}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            padding: "9px 12px",
            background: activeTab === "summary" ? "#10b9c9" : "#eef3f8",
            color: activeTab === "summary" ? "#0b1018" : "#101622",
            border: activeTab === "summary" ? "none" : "1px solid #d6dee9",
            borderRadius: "8px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            fontWeight: 700,
            cursor: "pointer",
            textTransform: "uppercase",
            letterSpacing: "0.4px",
          }}
        >
          <FileText size={14} />
          Summary
        </button>
        <button
          onClick={() => setActiveTab("code")}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            padding: "9px 12px",
            background: activeTab === "code" ? "#10b9c9" : "#eef3f8",
            color: activeTab === "code" ? "#0b1018" : "#101622",
            border: activeTab === "code" ? "none" : "1px solid #d6dee9",
            borderRadius: "8px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            fontWeight: 700,
            cursor: "pointer",
            textTransform: "uppercase",
            letterSpacing: "0.4px",
          }}
        >
          <Code size={14} />
          Code
        </button>
      </div>

      {/* Content */}
      <div>
        {activeTab === "summary" && <MarkdownView />}
        {activeTab === "code" && <CodeView />}
      </div>
    </div>
  );
}
