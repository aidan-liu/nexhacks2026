"use client";

import { Users, FileCheck } from "lucide-react";

export function StakeholdersSection() {
  return (
    <div
      style={{
        background: "#ffffff",
        border: "1px solid #e1e7f0",
        borderRadius: "10px",
        padding: "8px",
      }}
    >
      {/* Header */}
      <h3
        style={{
          margin: "0 0 12px 0",
          fontFamily: "'Space Mono', monospace",
          fontSize: "12px",
          letterSpacing: "0.6px",
          textTransform: "uppercase",
          color: "#667085",
        }}
      >
        Stakeholders & Terms
      </h3>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
        {/* Stakeholders */}
        <div>
          <h4
            style={{
              margin: "0 0 10px 0",
              fontFamily: "'Space Mono', monospace",
              fontSize: "11px",
              color: "#101622",
              display: "flex",
              alignItems: "center",
              gap: "6px",
              textTransform: "uppercase",
              letterSpacing: "0.4px",
            }}
          >
            <Users size={14} color="#10b9c9" />
            Stakeholders
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            <StakeholderItem
              role="Inspector"
              description="Government administrator or AI agent responsible for verifying proof of work"
            />
            <StakeholderItem
              role="Recipients"
              description="Contractors or service providers who complete work and claim funds"
            />
            <StakeholderItem
              role="Contract Owner"
              description="Entity that deployed the contract and manages initial configuration"
            />
            <StakeholderItem
              role="Public"
              description="Citizens who can view all transactions and verify fund usage"
            />
          </div>
        </div>

        {/* Terms & Conditions */}
        <div>
          <h4
            style={{
              margin: "0 0 10px 0",
              fontFamily: "'Space Mono', monospace",
              fontSize: "11px",
              color: "#101622",
              display: "flex",
              alignItems: "center",
              gap: "6px",
              textTransform: "uppercase",
              letterSpacing: "0.4px",
            }}
          >
            <FileCheck size={14} color="#f4a338" />
            Terms & Conditions
          </h4>
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            <TermItem
              term="24-Hour Dispute Period"
              description="Mandatory waiting period after verification before funds can be claimed"
            />
            <TermItem
              term="AI Verification"
              description="Proof documents are reviewed by an AI system for compliance"
            />
            <TermItem
              term="Proof Document Upload"
              description="Recipients must submit evidence (receipts, photos) of completed work"
            />
            <TermItem
              term="Immutable Records"
              description="All actions are permanently recorded on the blockchain"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function StakeholderItem({
  role,
  description,
}: {
  role: string;
  description: string;
}) {
  return (
    <div style={{ display: "flex", gap: "10px", alignItems: "flex-start" }}>
      <div
        style={{
          width: "6px",
          height: "6px",
          borderRadius: "50%",
          background: "#10b9c9",
          marginTop: "5px",
          flexShrink: 0,
        }}
      />
      <div>
        <p
          style={{
            margin: 0,
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            fontWeight: 700,
            color: "#101622",
          }}
        >
          {role}
        </p>
        <p
          style={{
            margin: "2px 0 0 0",
            fontFamily: "'Space Mono', monospace",
            fontSize: "10px",
            color: "#667085",
            lineHeight: 1.4,
          }}
        >
          {description}
        </p>
      </div>
    </div>
  );
}

function TermItem({ term, description }: { term: string; description: string }) {
  return (
    <div style={{ display: "flex", gap: "10px", alignItems: "flex-start" }}>
      <div
        style={{
          width: "6px",
          height: "6px",
          borderRadius: "50%",
          background: "#f4a338",
          marginTop: "5px",
          flexShrink: 0,
        }}
      />
      <div>
        <p
          style={{
            margin: 0,
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            fontWeight: 700,
            color: "#101622",
          }}
        >
          {term}
        </p>
        <p
          style={{
            margin: "2px 0 0 0",
            fontFamily: "'Space Mono', monospace",
            fontSize: "10px",
            color: "#667085",
            lineHeight: 1.4,
          }}
        >
          {description}
        </p>
      </div>
    </div>
  );
}
