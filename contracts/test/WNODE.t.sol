// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Test} from "forge-std/Test.sol";
import {WNODE} from "../src/WNODE.sol";

contract WNODETest is Test {
    WNODE w;
    address alice = makeAddr("alice");
    address bob = makeAddr("bob");

    function setUp() public {
        w = new WNODE();
        vm.deal(alice, 100 ether);
    }

    function testDepositAndWithdraw() public {
        vm.prank(alice);
        w.deposit{value: 10 ether}();
        assertEq(w.balanceOf(alice), 10 ether);
        assertEq(w.totalSupply(), 10 ether);
        assertEq(address(w).balance, 10 ether);
        vm.prank(alice);
        w.withdraw(4 ether);
        assertEq(w.balanceOf(alice), 6 ether);
        assertEq(alice.balance, 94 ether);
        assertEq(address(w).balance, w.totalSupply());
    }

    function testPlainSendWraps() public {
        vm.prank(alice);
        (bool ok,) = address(w).call{value: 1 ether}("");
        assertTrue(ok);
        assertEq(w.balanceOf(alice), 1 ether);
    }

    function testCannotWithdrawMoreThanBalance() public {
        vm.prank(alice);
        w.deposit{value: 1 ether}();
        vm.prank(alice);
        vm.expectRevert();
        w.withdraw(2 ether);
    }

    function testTransferAndAllowance() public {
        vm.startPrank(alice);
        w.deposit{value: 5 ether}();
        w.transfer(bob, 2 ether);
        w.approve(bob, 1 ether);
        vm.stopPrank();
        assertEq(w.balanceOf(bob), 2 ether);
        vm.prank(bob);
        w.transferFrom(alice, bob, 1 ether);
        assertEq(w.balanceOf(bob), 3 ether);
        assertEq(w.allowance(alice, bob), 0);
        vm.prank(bob);
        vm.expectRevert();
        w.transferFrom(alice, bob, 1);
    }

    function testInfiniteAllowanceIsNotSpent() public {
        vm.startPrank(alice);
        w.deposit{value: 2 ether}();
        w.approve(bob, type(uint256).max);
        vm.stopPrank();
        vm.prank(bob);
        w.transferFrom(alice, bob, 1 ether);
        assertEq(w.allowance(alice, bob), type(uint256).max);
    }

    function testTransferToZeroRejected() public {
        vm.prank(alice);
        w.deposit{value: 1 ether}();
        vm.prank(alice);
        vm.expectRevert();
        w.transfer(address(0), 1 ether);
    }

    /// Backing invariant: whatever is deposited and withdrawn, contract NODE equals WNODE supply.
    function testFuzzBacking(uint96 dep, uint96 out) public {
        vm.deal(alice, uint256(dep));
        vm.prank(alice);
        w.deposit{value: dep}();
        uint256 take = bound(out, 0, dep);
        vm.prank(alice);
        w.withdraw(take);
        assertEq(address(w).balance, w.totalSupply());
        assertEq(w.totalSupply(), uint256(dep) - take);
    }
}