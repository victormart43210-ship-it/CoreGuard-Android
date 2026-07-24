package cmd

import (
	"encoding/json"
	"fmt"
	"io"
	"strings"

	"github.com/spf13/cobra"
	"github.com/victormart43210-ship-it/CoreGuard-Android/cli/internal/sim"
)

// NewRootCommand creates a CLI backed by one in-memory lab engine.
func NewRootCommand() *cobra.Command {
	return NewRootCommandWithEngine(sim.NewEngine())
}

// NewRootCommandWithEngine allows command tests to share state across invocations.
func NewRootCommandWithEngine(engine *sim.Engine) *cobra.Command {
	root := &cobra.Command{
		Use:           "coreguard",
		Short:         "CoreGuard Network Defense Lab CLI",
		Long:          "CoreGuard Network Defense Lab CLI.\n\nPrototype note: lab state is in memory only and lasts for the lifetime of this process.",
		SilenceUsage:  true,
		SilenceErrors: true,
	}

	root.AddCommand(newStatusCommand(engine))
	root.AddCommand(newTopologyCommand(engine))
	root.AddCommand(newSimulateCommand(engine))

	return root
}

func newStatusCommand(engine *sim.Engine) *cobra.Command {
	var outputJSON bool
	command := &cobra.Command{
		Use:   "status",
		Short: "Show current lab status",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			report := engine.Status()
			if outputJSON {
				return writeJSON(cmd.OutOrStdout(), report)
			}
			printStatus(cmd.OutOrStdout(), report)
			return nil
		},
	}
	command.Flags().BoolVar(&outputJSON, "json", false, "emit JSON output")
	return command
}

func newTopologyCommand(engine *sim.Engine) *cobra.Command {
	var outputJSON bool
	var includeMST bool
	command := &cobra.Command{
		Use:   "topology",
		Short: "Show fixed lab topology",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			report, err := engine.Topology(includeMST)
			if err != nil {
				return err
			}
			if outputJSON {
				return writeJSON(cmd.OutOrStdout(), report)
			}
			printTopology(cmd.OutOrStdout(), report)
			return nil
		},
	}
	command.Flags().BoolVar(&outputJSON, "json", false, "emit JSON output")
	command.Flags().BoolVar(&includeMST, "mst", false, "include Prim MST hardening guidance")
	return command
}

func newSimulateCommand(engine *sim.Engine) *cobra.Command {
	command := &cobra.Command{
		Use:   "simulate",
		Short: "Run attack, defense, or rollback simulation operations",
	}
	command.AddCommand(newAttackCommand(engine))
	command.AddCommand(newDefendCommand(engine))
	command.AddCommand(newRollbackCommand(engine))
	return command
}

func newAttackCommand(engine *sim.Engine) *cobra.Command {
	var mode string
	var seed string
	var steps int
	var outputJSON bool
	command := &cobra.Command{
		Use:   "attack",
		Short: "Simulate a BFS or DFS attack",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			result, err := engine.Attack(sim.AttackMode(strings.ToLower(mode)), seed, steps)
			if err != nil {
				return err
			}
			if outputJSON {
				return writeJSON(cmd.OutOrStdout(), result)
			}
			printAttack(cmd.OutOrStdout(), result)
			return nil
		},
	}
	command.Flags().StringVar(&mode, "mode", string(sim.AttackBFS), "attack mode: bfs or dfs")
	command.Flags().StringVar(&seed, "seed", "N0", "seed node, e.g. N3")
	command.Flags().IntVar(&steps, "steps", 4, "maximum nodes to compromise")
	command.Flags().BoolVar(&outputJSON, "json", false, "emit JSON output")
	return command
}

func newDefendCommand(engine *sim.Engine) *cobra.Command {
	var mode string
	var target string
	var outputJSON bool
	command := &cobra.Command{
		Use:   "defend",
		Short: "Apply direct or cascade defense",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			result, err := engine.Defend(sim.DefenseMode(strings.ToLower(mode)), target)
			if err != nil {
				return err
			}
			if outputJSON {
				return writeJSON(cmd.OutOrStdout(), result)
			}
			printDefense(cmd.OutOrStdout(), result)
			return nil
		},
	}
	command.Flags().StringVar(&mode, "mode", string(sim.DefenseDirect), "defense mode: direct or cascade")
	command.Flags().StringVar(&target, "target", "N0", "target node, e.g. N3")
	command.Flags().BoolVar(&outputJSON, "json", false, "emit JSON output")
	return command
}

func newRollbackCommand(engine *sim.Engine) *cobra.Command {
	var outputJSON bool
	command := &cobra.Command{
		Use:   "rollback",
		Short: "Restore the previous lab snapshot",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, _ []string) error {
			report, err := engine.Rollback()
			if err != nil {
				return err
			}
			if outputJSON {
				return writeJSON(cmd.OutOrStdout(), report)
			}
			fmt.Fprintln(cmd.OutOrStdout(), "Rollback restored the previous lab snapshot.")
			printStatus(cmd.OutOrStdout(), report)
			return nil
		},
	}
	command.Flags().BoolVar(&outputJSON, "json", false, "emit JSON output")
	return command
}

func writeJSON(writer io.Writer, value any) error {
	encoder := json.NewEncoder(writer)
	encoder.SetIndent("", "  ")
	return encoder.Encode(value)
}

func printStatus(writer io.Writer, report sim.StatusReport) {
	fmt.Fprintln(writer, "CoreGuard Network Defense Lab status")
	printMetrics(writer, report.Metrics)
	fmt.Fprintln(writer, "Nodes:")
	for _, node := range report.Nodes {
		fmt.Fprintf(writer, "  %s: %s\n", node.ID, node.State)
	}
	printEvents(writer, report.Events)
}

func printTopology(writer io.Writer, report sim.TopologyReport) {
	fmt.Fprintln(writer, "CoreGuard Network Defense Lab topology")
	fmt.Fprintf(writer, "Nodes: %s\n", strings.Join(report.Nodes, ", "))
	fmt.Fprintln(writer, "Edges:")
	for _, edge := range report.Edges {
		fmt.Fprintf(writer, "  %s -- %s (weight %d)\n", edge.From, edge.To, edge.Weight)
	}
	if len(report.MSTEdges) > 0 {
		fmt.Fprintln(writer, "Hardening backbone (Prim MST from N0):")
		for _, edge := range report.MSTEdges {
			fmt.Fprintf(writer, "  %s -- %s (weight %d)\n", edge.From, edge.To, edge.Weight)
		}
	}
}

func printAttack(writer io.Writer, result sim.AttackResult) {
	fmt.Fprintf(writer, "Attack %s from %s infected: %s\n", result.Mode, result.Seed, formatList(result.Infected))
	printMetrics(writer, result.Metrics)
	printEvents(writer, result.Events)
}

func printDefense(writer io.Writer, result sim.DefenseResult) {
	fmt.Fprintf(writer, "Defense %s on %s defended: %s\n", result.Mode, result.Target, formatList(result.Defended))
	printMetrics(writer, result.Metrics)
	printEvents(writer, result.Events)
}

func printMetrics(writer io.Writer, metrics sim.Metrics) {
	fmt.Fprintf(writer, "Metrics: compromised=%d defended=%d healthy=%d stepsTaken=%d\n",
		metrics.CompromisedCount,
		metrics.DefendedCount,
		metrics.HealthyCount,
		metrics.StepsTaken,
	)
}

func printEvents(writer io.Writer, events []sim.Event) {
	fmt.Fprintln(writer, "Events:")
	if len(events) == 0 {
		fmt.Fprintln(writer, "  none")
		return
	}
	for _, event := range events {
		fmt.Fprintf(writer, "  %s %s: %s\n", event.TS, event.Kind, event.Detail)
	}
}

func formatList(values []string) string {
	if len(values) == 0 {
		return "[]"
	}
	return "[" + strings.Join(values, ", ") + "]"
}
