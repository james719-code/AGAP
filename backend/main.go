package main

import (
	"os"
	"os/exec"
)

// Main entry point shim for `go run main.go` inside backend directory
func main() {
	cmd := exec.Command("go", "run", "./cmd/server/main.go")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	if err := cmd.Run(); err != nil {
		os.Exit(1)
	}
}
