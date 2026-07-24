package main

import (
	"fmt"
	"os"

	"github.com/victormart43210-ship-it/CoreGuard-Android/cli/internal/cmd"
)

func main() {
	if err := cmd.NewRootCommand().Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
