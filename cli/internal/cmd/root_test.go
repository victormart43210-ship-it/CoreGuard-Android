package cmd

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"

	"github.com/victormart43210-ship-it/CoreGuard-Android/cli/internal/sim"
)

func TestAttackCommandJSON(t *testing.T) {
	engine := sim.NewEngine()

	output, err := executeCommand(engine, "simulate", "attack", "--mode", "bfs", "--seed", "N0", "--steps", "2", "--json")
	if err != nil {
		t.Fatalf("executeCommand() error = %v", err)
	}

	var result sim.AttackResult
	if err := json.Unmarshal([]byte(output), &result); err != nil {
		t.Fatalf("json.Unmarshal() error = %v\noutput: %s", err, output)
	}
	if result.Mode != sim.AttackBFS || result.Seed != "N0" || result.StepsTaken != 2 {
		t.Fatalf("result = %+v, want bfs N0 with 2 steps", result)
	}
	if got := strings.Join(result.Infected, ","); got != "N0,N1" {
		t.Fatalf("infected = %s, want N0,N1", got)
	}
	if len(result.Events) != 1 || result.Events[0].Kind != "attack" {
		t.Fatalf("events = %+v, want one attack event", result.Events)
	}
}

func TestStatusReflectsSharedInMemoryEngine(t *testing.T) {
	engine := sim.NewEngine()

	if _, err := executeCommand(engine, "simulate", "attack", "--mode", "dfs", "--seed", "N0", "--steps", "3"); err != nil {
		t.Fatalf("attack command error = %v", err)
	}
	output, err := executeCommand(engine, "status", "--json")
	if err != nil {
		t.Fatalf("status command error = %v", err)
	}

	var report sim.StatusReport
	if err := json.Unmarshal([]byte(output), &report); err != nil {
		t.Fatalf("json.Unmarshal() error = %v\noutput: %s", err, output)
	}
	if report.Metrics.CompromisedCount != 3 || report.Metrics.StepsTaken != 3 {
		t.Fatalf("metrics = %+v, want compromised=3 stepsTaken=3", report.Metrics)
	}
}

func TestTopologyCommandShowsMSTGuidance(t *testing.T) {
	output, err := executeCommand(sim.NewEngine(), "topology", "--mst")
	if err != nil {
		t.Fatalf("topology command error = %v", err)
	}

	if !strings.Contains(output, "Hardening backbone (Prim MST from N0):") {
		t.Fatalf("output missing MST heading:\n%s", output)
	}
	if !strings.Contains(output, "N0 -- N1") {
		t.Fatalf("output missing expected MST edge:\n%s", output)
	}
}

func TestRollbackCommandRestoresSharedEngineSnapshot(t *testing.T) {
	engine := sim.NewEngine()

	if _, err := executeCommand(engine, "simulate", "attack", "--mode", "bfs", "--seed", "N0", "--steps", "2"); err != nil {
		t.Fatalf("attack command error = %v", err)
	}
	output, err := executeCommand(engine, "simulate", "rollback", "--json")
	if err != nil {
		t.Fatalf("rollback command error = %v", err)
	}

	var report sim.StatusReport
	if err := json.Unmarshal([]byte(output), &report); err != nil {
		t.Fatalf("json.Unmarshal() error = %v\noutput: %s", err, output)
	}
	if report.Metrics.CompromisedCount != 0 || report.Metrics.HealthyCount != 16 {
		t.Fatalf("metrics = %+v, want all nodes healthy after rollback", report.Metrics)
	}
	if len(report.Events) != 2 || report.Events[1].Kind != "rollback" {
		t.Fatalf("events = %+v, want attack then rollback", report.Events)
	}
}

func executeCommand(engine *sim.Engine, args ...string) (string, error) {
	root := NewRootCommandWithEngine(engine)
	buffer := new(bytes.Buffer)
	root.SetOut(buffer)
	root.SetErr(buffer)
	root.SetArgs(args)
	err := root.Execute()
	return buffer.String(), err
}
