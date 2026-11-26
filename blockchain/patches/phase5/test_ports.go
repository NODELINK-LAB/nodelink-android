package network

// NODELINK: the vendored test network takes RPC, API and JSON-RPC ports from portPool but upstream never fills it
// in this package, so every in-process network failed with "failed to get port". Fill it with free local ports.

import (
	"net"
	"strconv"
)

func init() {
	ls := make([]net.Listener, 0, cap(portPool))
	for len(ls) < cap(portPool) {
		l, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			break
		}
		ls = append(ls, l)
	}
	for _, l := range ls { // all held open at once, so no port is handed out twice
		portPool <- strconv.Itoa(l.Addr().(*net.TCPAddr).Port)
		_ = l.Close()
	}
}