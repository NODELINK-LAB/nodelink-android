// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

import {Test} from "forge-std/Test.sol";
import {NodeVesting} from "../src/NodeVesting.sol";

contract NodeVestingTest is Test {
    NodeVesting v;
    address team = makeAddr("team");
    uint64 t0 = 1_000_000;
    uint64 constant YEAR = 365 days;

    function setUp() public {
        vm.warp(t0);
        v = new NodeVesting(team, t0, YEAR, 4 * YEAR); // 1-year cliff, 4-year linear
        vm.deal(address(this), 1000 ether);
        (bool ok,) = address(v).call{value: 1000 ether}("");
        assertTrue(ok);
    }

    function testNothingBeforeCliff() public {
        vm.warp(t0 + YEAR - 1);
        assertEq(v.releasable(), 0);
        vm.expectRevert(bytes("Vesting: nothing to release"));
        v.release();
    }

    function testLinearAfterCliff() public {
        vm.warp(t0 + YEAR);
        assertEq(v.releasable(), 250 ether);
        vm.warp(t0 + 2 * YEAR);
        assertEq(v.releasable(), 500 ether);
    }

    function testAnyoneReleasesToBeneficiary() public {
        vm.warp(t0 + 2 * YEAR);
        vm.prank(makeAddr("stranger"));
        v.release();
        assertEq(team.balance, 500 ether);
        assertEq(v.released(), 500 ether);
        assertEq(v.releasable(), 0);
    }

    function testAllAtEnd() public {
        vm.warp(t0 + 2 * YEAR);
        v.release();
        vm.warp(t0 + 10 * YEAR);
        v.release();
        assertEq(team.balance, 1000 ether);
        assertEq(address(v).balance, 0);
    }

    function testLateTopUpFollowsSchedule() public {
        vm.warp(t0 + 2 * YEAR);
        v.release(); // 500 out
        vm.deal(address(this), 1000 ether);
        (bool ok,) = address(v).call{value: 1000 ether}("");
        assertTrue(ok);
        assertEq(v.releasable(), 500 ether); // half of 2000 vested, 500 already released
    }

    function testBadScheduleRejected() public {
        vm.expectRevert(bytes("Vesting: bad schedule"));
        new NodeVesting(team, t0, 5 * YEAR, 4 * YEAR);
        vm.expectRevert(bytes("Vesting: zero beneficiary"));
        new NodeVesting(address(0), t0, 0, YEAR);
    }
}