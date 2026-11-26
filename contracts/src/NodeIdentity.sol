// SPDX-License-Identifier: MIT
pragma solidity 0.8.24;

/// Each wallet can permanently register one public display name. Names are not exclusive.
contract NodeIdentity {
    uint256 public constant registryVersion = 1;
    mapping(address => string) public nameOf;
    event NameRegistered(address indexed wallet, string name);
    error AlreadyRegistered();
    error InvalidName();

    function register(string calldata name) external {
        if (bytes(nameOf[msg.sender]).length != 0) revert AlreadyRegistered();
        bytes calldata chars = bytes(name);
        if (chars.length < 3 || chars.length > 24) revert InvalidName();
        for (uint256 i; i < chars.length; ++i) {
            bytes1 ch = chars[i];
            bool alphanumeric = (ch >= 0x61 && ch <= 0x7a) || (ch >= 0x30 && ch <= 0x39);
            if (!alphanumeric && !(i > 0 && i + 1 < chars.length && (ch == 0x2d || ch == 0x5f))) {
                revert InvalidName();
            }
        }
        nameOf[msg.sender] = name;
        emit NameRegistered(msg.sender, name);
    }
}