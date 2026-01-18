export const CONTRACT_CONFIG = {
  address: "0xE54c377Ee76ed5E9bC89e43B41d0C3925f8D027e",
  network: "Sepolia Testnet",
  chainId: 11155111,
  explorerUrl: "https://sepolia.etherscan.io",
  explorerApiUrl: "https://api-sepolia.etherscan.io/api",
};

export const CONTRACT_SOURCE = `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

contract BudgetEscrow {

    // --- 1. DATA STRUCTURES (The "Bill") ---

    struct BudgetItem {
        uint256 id;                 // Unique ID (e.g., 1)
        string description;         // "Repair Main St Bridge"
        uint256 amount;             // How much money? (in Wei)
        address payable recipient;  // Construction Co. Wallet
        string proofDocumentUrl;    // Link to the receipt/photo
        bool isVerified;            // Has the Inspector/AI said "YES"?
        uint256 disputeEndTime;     // When can they withdraw?
        bool isPaid;                // Has the money left the vault?
    }

    // A "Database" to store all our budget items
    mapping(uint256 => BudgetItem) public budgetItems;
    uint256 public itemCount;

    // The "Inspector" (The Government Admin or your future AI Agent)
    address public inspector;

    // --- 2. EVENTS (The "Signal Flare") ---
    // These allow your future Python script to "hear" when things happen
    event BudgetCreated(uint256 id, string description, uint256 amount);
    event ProofUploaded(uint256 id, string url);
    event ItemVerified(uint256 id, uint256 disputeEndTime);
    event FundsReleased(uint256 id, uint256 amount);

    constructor() {
        inspector = msg.sender; // The person deploying (YOU) is the Inspector for now
    }

    // --- 3. THE LOGIC ---

    // Function A: Create a Budget Item (Put money in the Vault)
    // "msg.value" is the actual crypto sent with the transaction
    function createBudgetItem(string memory _description, address payable _recipient) public payable {
        itemCount++;

        budgetItems[itemCount] = BudgetItem({
            id: itemCount,
            description: _description,
            amount: msg.value,       // The amount sent with this transaction
            recipient: _recipient,
            proofDocumentUrl: "",
            isVerified: false,
            disputeEndTime: 0,
            isPaid: false
        });

        emit BudgetCreated(itemCount, _description, msg.value);
    }

    // Function B: The Construction Company uploads proof
    function uploadProof(uint256 _id, string memory _url) public {
        BudgetItem storage item = budgetItems[_id];
        require(msg.sender == item.recipient, "Only the recipient can upload proof");

        item.proofDocumentUrl = _url;

        // Fire the event! Your Python AI script will be listening for THIS specific signal.
        emit ProofUploaded(_id, _url);
    }

    // Function C: The Inspector (or AI) verifies the proof
    function verifyProof(uint256 _id) public {
        require(msg.sender == inspector, "Only the Inspector can verify");
        BudgetItem storage item = budgetItems[_id];

        item.isVerified = true;
        item.disputeEndTime = block.timestamp + 1 days; // Start 24h timer

        emit ItemVerified(_id, item.disputeEndTime);
    }

    // Function D: Release the Funds (After 24h)
    function claimFunds(uint256 _id) public {
        BudgetItem storage item = budgetItems[_id];

        require(item.isVerified == true, "Item not verified yet");
        require(block.timestamp > item.disputeEndTime, "Dispute period not over");
        require(item.isPaid == false, "Already paid");

        item.isPaid = true;

        // Transfer the actual ETH from the contract to the recipient
        (bool success, ) = item.recipient.call{value: item.amount}("");
        require(success, "Transfer failed");

        emit FundsReleased(_id, item.amount);
    }
}`;

// Event signatures for parsing logs
export const EVENT_SIGNATURES = {
  BudgetCreated: "0x" + "BudgetCreated(uint256,string,uint256)".split("").reduce((acc, char) => acc + char.charCodeAt(0).toString(16), ""),
  ProofUploaded: "0x" + "ProofUploaded(uint256,string)".split("").reduce((acc, char) => acc + char.charCodeAt(0).toString(16), ""),
  ItemVerified: "0x" + "ItemVerified(uint256,uint256)".split("").reduce((acc, char) => acc + char.charCodeAt(0).toString(16), ""),
  FundsReleased: "0x" + "FundsReleased(uint256,uint256)".split("").reduce((acc, char) => acc + char.charCodeAt(0).toString(16), ""),
};
