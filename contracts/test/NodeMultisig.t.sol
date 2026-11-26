// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Test} from "forge-std/Test.sol";
import {NodeMultisig} from "../src/NodeMultisig.sol";

contract Reverter {
    fallback() external payable {
        revert("no");
    }
}

contract NodeMultisigTest is Test {
    NodeMultisig m;
    address a = makeAddr("a");
    address b = makeAddr("b");
    address c = makeAddr("c");
    address dest = makeAddr("dest");

    function setUp() public {
        address[] memory o = new address[](3);
        o[0] = a; o[1] = b; o[2] = c;
        m = new NodeMultisig(o, 2);
        vm.deal(address(m), 100 ether);
    }

    function testTwoOfThreePays() public {
        vm.prank(a);
        uint256 id = m.submit(dest, 10 ether, "");
        vm.prank(a);
        vm.expectRevert(bytes("Multisig: not enough confirmations"));
        m.execute(id);
        vm.prank(b);
        m.confirm(id);
        vm.prank(c);
        m.execute(id);
        assertEq(dest.balance, 10 ether);
        vm.prank(a);
        vm.expectRevert(bytes("Multisig: already executed"));
        m.execute(id);
    }

    function testOutsiderRejected() public {
        vm.prank(dest);
        vm.expectRevert(bytes("Multisig: not owner"));
        m.submit(dest, 1 ether, "");
    }

    function testNoDoubleConfirm() public {
        vm.prank(a);
        uint256 id = m.submit(dest, 1 ether, "");
        vm.prank(a);
        vm.expectRevert(bytes("Multisig: already confirmed"));
        m.confirm(id);
    }

    function testRevokeBlocksExecution() public {
        vm.prank(a);
        uint256 id = m.submit(dest, 1 ether, "");
        vm.prank(b);
        m.confirm(id);
        vm.prank(b);
        m.revoke(id);
        vm.prank(a);
        vm.expectRevert(bytes("Multisig: not enough confirmations"));
        m.execute(id);
    }

    function testFailedCallStaysPending() public {
        address r = address(new Reverter());
        vm.prank(a);
        uint256 id = m.submit(r, 1 ether, "");
        vm.prank(b);
        m.confirm(id);
        vm.prank(a);
        vm.expectRevert(bytes("Multisig: call failed"));
        m.execute(id);
        (,,, bool executed,) = m.proposals(id);
        assertFalse(executed);
        assertEq(address(m).balance, 100 ether);
    }

    function testBadSetupRejected() public {
        address[] memory o = new address[](2);
        o[0] = a; o[1] = a;
        vm.expectRevert(bytes("Multisig: duplicate owner"));
        new NodeMultisig(o, 1);
        o[1] = b;
        vm.expectRevert(bytes("Multisig: bad threshold"));
        new NodeMultisig(o, 3);
    }
}