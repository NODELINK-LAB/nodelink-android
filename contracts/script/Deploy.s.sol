// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Script, console} from "forge-std/Script.sol";
import {WNODE} from "../src/WNODE.sol";
import {NodeMultisig} from "../src/NodeMultisig.sol";

/// Deploys WNODE and a treasury multisig.
/// Env: OWNERS (comma-separated 0x addresses), THRESHOLD (number of confirmations).
contract Deploy is Script {
    function run() external {
        address[] memory owners = vm.envAddress("OWNERS", ",");
        uint256 threshold = vm.envUint("THRESHOLD");
        vm.startBroadcast();
        WNODE w = new WNODE();
        NodeMultisig m = new NodeMultisig(owners, threshold);
        vm.stopBroadcast();
        console.log("WNODE", address(w));
        console.log("NodeMultisig", address(m));
    }
}