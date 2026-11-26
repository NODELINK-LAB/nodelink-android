// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

/// @title NodeMultisig
/// @notice M-of-N wallet for treasury NODE. Any owner proposes a call, it runs only after
///         `threshold` owners confirm. The owner set and threshold are fixed at deployment.
contract NodeMultisig {
    struct Proposal {
        address to;
        uint256 value;
        bytes data;
        bool executed;
        uint256 confirmations;
    }

    address[] private owners;
    mapping(address => bool) public isOwner;
    uint256 public immutable threshold;
    Proposal[] public proposals;
    mapping(uint256 => mapping(address => bool)) public confirmed;

    event Deposit(address indexed from, uint256 value);
    event Submitted(uint256 indexed id, address indexed owner, address to, uint256 value);
    event Confirmed(uint256 indexed id, address indexed owner);
    event Revoked(uint256 indexed id, address indexed owner);
    event Executed(uint256 indexed id);

    modifier onlyOwner() {
        require(isOwner[msg.sender], "Multisig: not owner");
        _;
    }

    modifier pending(uint256 id) {
        require(id < proposals.length, "Multisig: no such proposal");
        require(!proposals[id].executed, "Multisig: already executed");
        _;
    }

    constructor(address[] memory owners_, uint256 threshold_) {
        require(threshold_ > 0 && threshold_ <= owners_.length, "Multisig: bad threshold");
        for (uint256 i = 0; i < owners_.length; i++) {
            address o = owners_[i];
            require(o != address(0), "Multisig: zero owner");
            require(!isOwner[o], "Multisig: duplicate owner");
            isOwner[o] = true;
            owners.push(o);
        }
        threshold = threshold_;
    }

    receive() external payable {
        emit Deposit(msg.sender, msg.value);
    }

    function submit(address to, uint256 value, bytes calldata data) external onlyOwner returns (uint256 id) {
        id = proposals.length;
        proposals.push(Proposal(to, value, data, false, 0));
        emit Submitted(id, msg.sender, to, value);
        _confirm(id);
    }

    function confirm(uint256 id) external onlyOwner pending(id) {
        _confirm(id);
    }

    function revoke(uint256 id) external onlyOwner pending(id) {
        require(confirmed[id][msg.sender], "Multisig: not confirmed");
        confirmed[id][msg.sender] = false;
        proposals[id].confirmations -= 1;
        emit Revoked(id, msg.sender);
    }

    /// @notice A failing call reverts the whole transaction, so the proposal stays pending.
    function execute(uint256 id) external onlyOwner pending(id) {
        Proposal storage p = proposals[id];
        require(p.confirmations >= threshold, "Multisig: not enough confirmations");
        p.executed = true;
        (bool ok,) = p.to.call{value: p.value}(p.data);
        require(ok, "Multisig: call failed");
        emit Executed(id);
    }

    function getOwners() external view returns (address[] memory) {
        return owners;
    }

    function proposalCount() external view returns (uint256) {
        return proposals.length;
    }

    function _confirm(uint256 id) private {
        require(!confirmed[id][msg.sender], "Multisig: already confirmed");
        confirmed[id][msg.sender] = true;
        proposals[id].confirmations += 1;
        emit Confirmed(id, msg.sender);
    }
}