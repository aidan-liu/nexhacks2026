"use client";

const MARKDOWN_CONTENT = `# Budget Escrow Smart Contract

## Purpose
This contract securely holds government budget funds until work is verified. It acts as a transparent, tamper-proof escrow system that ensures public money is only released when obligations are fulfilled.

## How It Works

### Step 1: Budget Creation
A government representative deposits funds into the contract for a specific project (e.g., "Repair Main St Bridge"). The funds are locked in the contract until verification is complete.

### Step 2: Proof Submission
The contractor (recipient) uploads proof of work completion - this could be a receipt, photo, or document URL. Only the designated recipient can submit proof for their assigned budget item.

### Step 3: AI Verification
The Inspector (which can be an AI agent or government administrator) reviews the submitted proof and verifies that the work meets requirements. This verification is recorded on the blockchain.

### Step 4: 24-Hour Dispute Period
After verification, a mandatory 24-hour waiting period begins. This gives time for any stakeholder to raise objections before funds are released. This protection prevents hasty or fraudulent approvals.

### Step 5: Fund Release
Once the dispute period ends without objection, the contractor can claim the funds. The payment is automatically transferred from the contract to their wallet.

## Key Protections

- **Funds are Locked**: Money cannot be withdrawn without completing the full verification process
- **Transparent Records**: All actions are permanently recorded on the blockchain
- **Dispute Window**: 24-hour period allows stakeholders to raise concerns
- **Role-Based Access**: Only designated recipients can upload proof; only the Inspector can verify
- **Automatic Execution**: Once conditions are met, payments happen automatically without intermediaries

## For Representatives

This system ensures:
1. **Accountability** - Every transaction and approval is permanently recorded
2. **Efficiency** - Automated verification reduces bureaucratic delays
3. **Trust** - Citizens can independently verify how funds are used
4. **Fraud Prevention** - Multi-step verification with built-in waiting periods`;

export function MarkdownView() {
  return (
    <div
      style={{
        background: "#f9fbfe",
        border: "1px solid #e1e7f0",
        borderRadius: "8px",
        padding: "16px",
        maxHeight: "500px",
        overflowY: "auto",
      }}
    >
      {MARKDOWN_CONTENT.split("\n\n").map((block, i) => {
        // H1
        if (block.startsWith("# ")) {
          return (
            <h1
              key={i}
              style={{
                margin: "0 0 16px 0",
                paddingBottom: "8px",
                borderBottom: "1px solid #d6dee9",
                fontFamily: "'Space Mono', monospace",
                fontSize: "16px",
                fontWeight: 700,
                color: "#101622",
                letterSpacing: "0.5px",
              }}
            >
              {block.replace("# ", "")}
            </h1>
          );
        }

        // H2
        if (block.startsWith("## ")) {
          return (
            <h2
              key={i}
              style={{
                margin: "20px 0 10px 0",
                fontFamily: "'Space Mono', monospace",
                fontSize: "13px",
                fontWeight: 700,
                color: "#101622",
                textTransform: "uppercase",
                letterSpacing: "0.6px",
              }}
            >
              {block.replace("## ", "")}
            </h2>
          );
        }

        // H3
        if (block.startsWith("### ")) {
          return (
            <h3
              key={i}
              style={{
                margin: "14px 0 8px 0",
                fontFamily: "'Space Mono', monospace",
                fontSize: "12px",
                fontWeight: 700,
                color: "#10b9c9",
              }}
            >
              {block.replace("### ", "")}
            </h3>
          );
        }

        // Bullet list
        if (block.startsWith("- ")) {
          const items = block.split("\n").filter((line) => line.startsWith("- "));
          return (
            <ul
              key={i}
              style={{
                margin: "8px 0",
                paddingLeft: "0",
                listStyle: "none",
                display: "flex",
                flexDirection: "column",
                gap: "8px",
              }}
            >
              {items.map((item, j) => (
                <li
                  key={j}
                  style={{
                    display: "flex",
                    gap: "10px",
                    fontFamily: "'Space Mono', monospace",
                    fontSize: "11px",
                    color: "#101622",
                    lineHeight: 1.5,
                  }}
                >
                  <span style={{ color: "#10b9c9", flexShrink: 0 }}>•</span>
                  <span>{renderBoldText(item.replace("- ", ""))}</span>
                </li>
              ))}
            </ul>
          );
        }

        // Numbered list
        if (/^\d+\.\s/.test(block)) {
          const items = block.split("\n").filter((line) => /^\d+\.\s/.test(line));
          return (
            <ol
              key={i}
              style={{
                margin: "8px 0",
                paddingLeft: "0",
                listStyle: "none",
                display: "flex",
                flexDirection: "column",
                gap: "8px",
              }}
            >
              {items.map((item, j) => (
                <li
                  key={j}
                  style={{
                    display: "flex",
                    gap: "10px",
                    fontFamily: "'Space Mono', monospace",
                    fontSize: "11px",
                    color: "#101622",
                    lineHeight: 1.5,
                  }}
                >
                  <span style={{ color: "#f4a338", fontWeight: 700, flexShrink: 0 }}>{j + 1}.</span>
                  <span>{renderBoldText(item.replace(/^\d+\.\s/, ""))}</span>
                </li>
              ))}
            </ol>
          );
        }

        // Regular paragraph
        if (block.trim()) {
          return (
            <p
              key={i}
              style={{
                margin: "8px 0",
                fontFamily: "'Space Mono', monospace",
                fontSize: "11px",
                color: "#667085",
                lineHeight: 1.6,
              }}
            >
              {renderBoldText(block)}
            </p>
          );
        }

        return null;
      })}
    </div>
  );
}

function renderBoldText(text: string): React.ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, i) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return (
        <strong key={i} style={{ fontWeight: 700, color: "#101622" }}>
          {part.slice(2, -2)}
        </strong>
      );
    }
    return part;
  });
}
