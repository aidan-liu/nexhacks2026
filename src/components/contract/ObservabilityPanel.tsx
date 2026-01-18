"use client";

import { useEffect, useState } from "react";
import { Activity, Wallet, Package, Clock, ExternalLink } from "lucide-react";
import {
  getContractStats,
  formatAddress,
  formatTimestamp,
  type ContractStats,
  type ContractEvent,
  type Transaction,
} from "@/lib/blockchain-service";
import { CONTRACT_CONFIG } from "@/lib/contract-config";

export function ObservabilityPanel() {
  const [stats, setStats] = useState<ContractStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetchStats() {
      try {
        const data = await getContractStats();
        setStats(data);
      } catch (error) {
        console.error("Failed to fetch stats:", error);
      } finally {
        setLoading(false);
      }
    }

    fetchStats();
    const interval = setInterval(fetchStats, 15000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
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
        <h3
          style={{
            margin: "0 0 8px 0",
            fontFamily: "'Space Mono', monospace",
            fontSize: "12px",
            letterSpacing: "0.6px",
            textTransform: "uppercase",
            color: "#667085",
            display: "flex",
            alignItems: "center",
            gap: "8px",
          }}
        >
          <Activity size={14} />
          Observability
        </h3>
        <div
          style={{
            textAlign: "center",
            padding: "24px 0",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            color: "#667085",
          }}
        >
          Loading contract data...
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        background: "#ffffff",
        border: "1px solid #e1e7f0",
        borderRadius: "10px",
        padding: "8px",
        display: "flex",
        flexDirection: "column",
        gap: "12px",
      }}
    >
      {/* Header */}
      <h3
        style={{
          margin: 0,
          fontFamily: "'Space Mono', monospace",
          fontSize: "12px",
          letterSpacing: "0.6px",
          textTransform: "uppercase",
          color: "#667085",
          display: "flex",
          alignItems: "center",
          gap: "8px",
        }}
      >
        <Activity size={14} />
        Observability
      </h3>

      {/* Stats Grid */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px" }}>
        <StatCard
          icon={<Wallet size={14} color="#2f6fe5" />}
          label="Balance"
          value={`${stats?.balance.eth || "0.000000"} ETH`}
        />
        <StatCard
          icon={<Package size={14} color="#1f9c6b" />}
          label="Total Items"
          value={stats?.totalItems.toString() || "0"}
        />
      </div>

      {/* Recent Events */}
      <div>
        <h4
          style={{
            margin: "0 0 8px 0",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            color: "#667085",
            display: "flex",
            alignItems: "center",
            gap: "6px",
          }}
        >
          <Clock size={12} />
          RECENT EVENTS
        </h4>
        {stats?.events && stats.events.length > 0 ? (
          <div style={{ display: "flex", flexDirection: "column", gap: "6px", maxHeight: "160px", overflowY: "auto" }}>
            {stats.events.slice(0, 5).map((event, i) => (
              <EventRow key={i} event={event} />
            ))}
          </div>
        ) : (
          <p style={{ fontFamily: "'Space Mono', monospace", fontSize: "11px", color: "#667085", margin: 0 }}>
            No events yet
          </p>
        )}
      </div>

      {/* Transaction History */}
      <div>
        <h4
          style={{
            margin: "0 0 8px 0",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            color: "#667085",
          }}
        >
          TRANSACTION HISTORY
        </h4>
        {stats?.transactions && stats.transactions.length > 0 ? (
          <div style={{ display: "flex", flexDirection: "column", gap: "6px", maxHeight: "160px", overflowY: "auto" }}>
            {stats.transactions.slice(0, 5).map((tx, i) => (
              <TransactionRow key={i} tx={tx} />
            ))}
          </div>
        ) : (
          <p style={{ fontFamily: "'Space Mono', monospace", fontSize: "11px", color: "#667085", margin: 0 }}>
            No transactions yet
          </p>
        )}
      </div>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div
      style={{
        background: "#f4f7fb",
        border: "1px solid #d6dee9",
        borderRadius: "8px",
        padding: "10px",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "4px" }}>
        {icon}
        <span
          style={{
            fontFamily: "'Space Mono', monospace",
            fontSize: "10px",
            color: "#667085",
            textTransform: "uppercase",
            letterSpacing: "0.4px",
          }}
        >
          {label}
        </span>
      </div>
      <p
        style={{
          margin: 0,
          fontFamily: "'Space Mono', monospace",
          fontSize: "14px",
          fontWeight: 700,
          color: "#101622",
        }}
      >
        {value}
      </p>
    </div>
  );
}

function EventRow({ event }: { event: ContractEvent }) {
  const eventColors: Record<string, { bg: string; text: string; border: string }> = {
    BudgetCreated: { bg: "#eef3f8", text: "#2f6fe5", border: "#d6dee9" },
    ProofUploaded: { bg: "#fef6e7", text: "#f4a338", border: "#f4a338" },
    ItemVerified: { bg: "#e7f7ee", text: "#1f9c6b", border: "#2ac769" },
    FundsReleased: { bg: "#f3e8ff", text: "#8b5cf6", border: "#8b5cf6" },
  };

  const colors = eventColors[event.type] || { bg: "#f4f7fb", text: "#667085", border: "#d6dee9" };

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "6px 8px",
        background: "#f9fbfe",
        border: "1px solid #e1e7f0",
        borderRadius: "6px",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
        <span
          style={{
            display: "inline-block",
            padding: "2px 8px",
            background: colors.bg,
            border: `1px solid ${colors.border}`,
            borderRadius: "999px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "9px",
            color: colors.text,
            fontWeight: 700,
            textTransform: "uppercase",
            letterSpacing: "0.3px",
          }}
        >
          {event.type}
        </span>
        <span style={{ fontFamily: "'Space Mono', monospace", fontSize: "10px", color: "#667085" }}>
          #{event.itemId}
        </span>
      </div>
      <span style={{ fontFamily: "'Space Mono', monospace", fontSize: "10px", color: "#98a2b3" }}>
        {formatTimestamp(event.timestamp)}
      </span>
    </div>
  );
}

function TransactionRow({ tx }: { tx: Transaction }) {
  const openTx = () => {
    window.open(`${CONTRACT_CONFIG.explorerUrl}/tx/${tx.hash}`, "_blank");
  };

  return (
    <div
      onClick={openTx}
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "6px 8px",
        background: "#f9fbfe",
        border: "1px solid #e1e7f0",
        borderRadius: "6px",
        cursor: "pointer",
        transition: "background 0.2s ease",
      }}
      onMouseEnter={(e) => (e.currentTarget.style.background = "#f4f7fb")}
      onMouseLeave={(e) => (e.currentTarget.style.background = "#f9fbfe")}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
        <code style={{ fontFamily: "'Space Mono', monospace", fontSize: "10px", color: "#101622" }}>
          {formatAddress(tx.hash)}
        </code>
        {tx.methodName && (
          <span style={{ fontFamily: "'Space Mono', monospace", fontSize: "9px", color: "#667085" }}>
            ({tx.methodName})
          </span>
        )}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
        <span
          style={{
            display: "inline-block",
            padding: "2px 6px",
            background: tx.status === "confirmed" ? "#e7f7ee" : "#fef6e7",
            border: `1px solid ${tx.status === "confirmed" ? "#2ac769" : "#f4a338"}`,
            borderRadius: "999px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "9px",
            color: tx.status === "confirmed" ? "#1f9c6b" : "#f4a338",
            fontWeight: 700,
            textTransform: "uppercase",
          }}
        >
          {tx.status}
        </span>
        <ExternalLink size={10} color="#98a2b3" />
      </div>
    </div>
  );
}
