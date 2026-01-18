const { buildModule } = require("@nomicfoundation/hardhat-ignition/modules");

module.exports = buildModule("BudgetModule", (m) => {
  // Deploy the contract. No arguments needed for the constructor this time.
  const budget = m.contract("BudgetEscrow");

  return { budget };
});