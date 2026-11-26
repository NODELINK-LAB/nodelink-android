// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

/// @title NodeVesting
/// @notice Locks native NODE for one beneficiary and releases it linearly after a cliff.
///         Not revocable and has no owner: once funded, nobody can take the NODE back or speed it up.
///         Anyone may call release(); the NODE always goes to the beneficiary.
contract NodeVesting {
    address public immutable beneficiary;
    uint64 public immutable start;
    uint64 public immutable cliff;
    uint64 public immutable end;
    uint256 public released;

    event Funded(address indexed from, uint256 amount);
    event Released(uint256 amount);

    constructor(address beneficiary_, uint64 start_, uint64 cliffSeconds, uint64 durationSeconds) {
        require(beneficiary_ != address(0), "Vesting: zero beneficiary");
        require(durationSeconds > 0 && cliffSeconds <= durationSeconds, "Vesting: bad schedule");
        beneficiary = beneficiary_;
        start = start_;
        cliff = start_ + cliffSeconds;
        end = start_ + durationSeconds;
    }

    receive() external payable {
        emit Funded(msg.sender, msg.value);
    }

    /// @notice NODE vested at time t out of everything ever funded (held + already released).
    function vestedAmount(uint64 t) public view returns (uint256) {
        uint256 total = address(this).balance + released;
        if (t < cliff) return 0;
        if (t >= end) return total;
        return (total * (t - start)) / (end - start);
    }

    function releasable() public view returns (uint256) {
        return vestedAmount(uint64(block.timestamp)) - released;
    }

    function release() external {
        uint256 amount = releasable();
        require(amount > 0, "Vesting: nothing to release");
        released += amount;
        emit Released(amount);
        (bool ok,) = beneficiary.call{value: amount}("");
        require(ok, "Vesting: send failed");
    }
}