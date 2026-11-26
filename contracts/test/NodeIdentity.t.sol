// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Test} from "forge-std/Test.sol";
import {NodeIdentity} from "../src/NodeIdentity.sol";

contract NodeIdentityTest is Test {
    NodeIdentity private registry;
    address private constant ALICE = address(0xA11CE);
    address private constant BOB = address(0xB0B);

    function setUp() public { registry = new NodeIdentity(); }

    function testWalletOwnsItsEntry() public {
        vm.prank(ALICE);
        registry.register("node-alice");
        assertEq(registry.nameOf(ALICE), "node-alice");
        assertEq(registry.nameOf(BOB), "");
        vm.prank(BOB);
        registry.register("node-alice");
        assertEq(registry.nameOf(BOB), "node-alice");
    }

    function testCannotReplaceName() public {
        registry.register("node-alice");
        vm.expectRevert(NodeIdentity.AlreadyRegistered.selector);
        registry.register("new-name");
    }

    function testInvalidInputDoesNotRegister() public {
        vm.expectRevert(NodeIdentity.InvalidName.selector);
        registry.register("-Invalid");
        vm.expectRevert(NodeIdentity.InvalidName.selector);
        registry.register("aa");
        assertEq(registry.nameOf(address(this)), "");
    }
}