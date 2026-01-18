require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config(); // Load the .env file

module.exports = {
  solidity: "0.8.28",
  networks: {
    // We add Sepolia here so Hardhat knows how to talk to it
    sepolia: {
      url: process.env.RPC_URL,
      accounts: [process.env.PRIVATE_KEY],
    },
  },
  etherscan: {
    apiKey: process.env.ETHERSCAN_API_KEY, // Etherscan API V2 - single key for all networks
  },
};