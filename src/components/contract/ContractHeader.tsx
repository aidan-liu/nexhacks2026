"use client";

import { ArrowLeft, Copy, ExternalLink } from "lucide-react";
import { CONTRACT_CONFIG } from "@/lib/contract-config";
import { formatAddress } from "@/lib/blockchain-service";
import { toast } from "sonner";

export function ContractHeader() {
  const copyAddress = () => {
    navigator.clipboard.writeText(CONTRACT_CONFIG.address);
    toast.success("Address copied to clipboard");
  };

  const openEtherscan = () => {
    window.open(
      `${CONTRACT_CONFIG.explorerUrl}/address/${CONTRACT_CONFIG.address}`,
      "_blank"
    );
  };

  const goBack = () => {
    window.location.href = "http://localhost:8080";
  };

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "10px 20px 12px",
        background: "#ffffff",
        border: "1px solid #d6dee9",
        borderRadius: "12px",
        gap: "12px",
        flexWrap: "wrap",
      }}
    >
      {/* Left side: Back button + Title */}
      <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
        <button
          onClick={goBack}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            padding: "9px 12px",
            background: "#eef3f8",
            color: "#101622",
            border: "1px solid #d6dee9",
            borderRadius: "8px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            fontWeight: 700,
            cursor: "pointer",
            letterSpacing: "0.4px",
          }}
        >
          <ArrowLeft size={14} />
          BACK TO SIMULATION
        </button>
        <div>
          <h1
            style={{
              margin: 0,
              fontFamily: "'Space Mono', monospace",
              fontSize: "14px",
              fontWeight: 700,
              color: "#101622",
              letterSpacing: "1px",
              textTransform: "uppercase",
            }}
          >
            Smart Contract Viewer
          </h1>
          <p
            style={{
              margin: "4px 0 0 0",
              fontFamily: "'Space Mono', monospace",
              fontSize: "11px",
              color: "#667085",
            }}
          >
            Budget Escrow Contract
          </p>
        </div>
      </div>

      {/* Right side: Network badge + Address + Actions */}
      <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
        {/* Network Badge */}
        <span
          style={{
            display: "inline-block",
            padding: "4px 10px",
            background: "#eef3f8",
            border: "1px solid #d6dee9",
            borderRadius: "999px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            color: "#667085",
          }}
        >
          {CONTRACT_CONFIG.network}
        </span>

        {/* Address with Copy */}
        <div
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            padding: "4px 10px",
            background: "#f4f7fb",
            border: "1px solid #d6dee9",
            borderRadius: "999px",
          }}
        >
          <code
            style={{
              fontFamily: "'Space Mono', monospace",
              fontSize: "11px",
              color: "#101622",
            }}
          >
            {formatAddress(CONTRACT_CONFIG.address)}
          </code>
          <button
            onClick={copyAddress}
            style={{
              background: "none",
              border: "none",
              padding: "2px",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              color: "#667085",
            }}
          >
            <Copy size={12} />
          </button>
        </div>

        {/* Etherscan Button */}
        <button
          onClick={openEtherscan}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            padding: "9px 12px",
            background: "#10b9c9",
            color: "#0b1018",
            border: "none",
            borderRadius: "8px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            fontWeight: 700,
            cursor: "pointer",
          }}
        >
          <ExternalLink size={12} />
          View on Etherscan
        </button>
      </div>
    </div>
  );
}
