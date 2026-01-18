"use client";

import { CONTRACT_SOURCE } from "@/lib/contract-config";

export function CodeView() {
  const lines = CONTRACT_SOURCE.split("\n");

  return (
    <div style={{ position: "relative" }}>
      <div
        style={{
          position: "absolute",
          top: "8px",
          right: "8px",
          zIndex: 1,
        }}
      >
        <span
          style={{
            display: "inline-block",
            padding: "4px 10px",
            background: "#eef3f8",
            border: "1px solid #d6dee9",
            borderRadius: "999px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "10px",
            color: "#667085",
            textTransform: "uppercase",
            letterSpacing: "0.4px",
          }}
        >
          Solidity
        </span>
      </div>
      <div
        style={{
          background: "#f4f7fb",
          border: "1px solid #d6dee9",
          borderRadius: "8px",
          overflow: "auto",
          maxHeight: "500px",
        }}
      >
        <pre
          style={{
            margin: 0,
            padding: "12px",
            fontFamily: "'Space Mono', monospace",
            fontSize: "11px",
            lineHeight: 1.5,
          }}
        >
          {lines.map((line, i) => (
            <div key={i} style={{ display: "flex" }}>
              <span
                style={{
                  userSelect: "none",
                  color: "#98a2b3",
                  width: "32px",
                  textAlign: "right",
                  paddingRight: "12px",
                  flexShrink: 0,
                }}
              >
                {i + 1}
              </span>
              <code style={{ color: "#101622" }}>
                {highlightSolidity(line)}
              </code>
            </div>
          ))}
        </pre>
      </div>
    </div>
  );
}

function highlightSolidity(line: string): React.ReactNode {
  const keywords = ["contract", "struct", "function", "public", "payable", "memory", "storage", "require", "emit", "mapping", "address", "uint256", "string", "bool", "event", "constructor", "return", "if", "else", "pragma", "solidity"];
  const types = ["true", "false"];

  // Highlight comments
  if (line.trim().startsWith("//")) {
    return <span style={{ color: "#667085", fontStyle: "italic" }}>{line}</span>;
  }

  // Highlight strings
  const parts: React.ReactNode[] = [];
  let remaining = line;
  let key = 0;

  while (remaining.length > 0) {
    const stringMatch = remaining.match(/^([^"]*)"([^"]*)"/);
    if (stringMatch) {
      // Process text before string
      if (stringMatch[1]) {
        parts.push(<span key={key++}>{processWords(stringMatch[1], keywords, types, key)}</span>);
      }
      // Add string
      parts.push(<span key={key++} style={{ color: "#1f9c6b" }}>&quot;{stringMatch[2]}&quot;</span>);
      remaining = remaining.slice(stringMatch[0].length);
    } else {
      parts.push(<span key={key++}>{processWords(remaining, keywords, types, key)}</span>);
      break;
    }
  }

  return <>{parts}</>;
}

function processWords(text: string, keywords: string[], types: string[], startKey: number): React.ReactNode[] {
  const words = text.split(/(\s+|[(){}\[\];,.])/);
  let key = startKey;
  return words.map((word) => {
    if (keywords.includes(word)) {
      return <span key={key++} style={{ color: "#2f6fe5", fontWeight: 500 }}>{word}</span>;
    }
    if (types.includes(word)) {
      return <span key={key++} style={{ color: "#8b5cf6" }}>{word}</span>;
    }
    if (/^0x[0-9a-fA-F]+$/.test(word) || /^\d+$/.test(word)) {
      return <span key={key++} style={{ color: "#f4a338" }}>{word}</span>;
    }
    return word;
  });
}
