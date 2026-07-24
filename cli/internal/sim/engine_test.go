package sim

import (
	"reflect"
	"testing"
)

func TestDefaultGraphTopology(t *testing.T) {
	graph := DefaultGraph()

	if got := len(graph.Nodes()); got != 16 {
		t.Fatalf("Nodes() length = %d, want 16", got)
	}
	if got := len(graph.Edges()); got != 24 {
		t.Fatalf("Edges() length = %d, want 24", got)
	}

	neighbors, err := graph.Neighbors("N0")
	if err != nil {
		t.Fatalf("Neighbors() error = %v", err)
	}
	want := []string{"N1", "N8", "N15"}
	if !reflect.DeepEqual(neighbors, want) {
		t.Fatalf("Neighbors(N0) = %v, want %v", neighbors, want)
	}
}

func TestAttackBFSCompromisesExpectedOrder(t *testing.T) {
	engine := NewEngine()

	result, err := engine.Attack(AttackBFS, "N0", 4)
	if err != nil {
		t.Fatalf("Attack() error = %v", err)
	}

	want := []string{"N0", "N1", "N8", "N15"}
	if !reflect.DeepEqual(result.Infected, want) {
		t.Fatalf("infected = %v, want %v", result.Infected, want)
	}
	if result.Metrics.CompromisedCount != 4 || result.Metrics.HealthyCount != 12 || result.Metrics.StepsTaken != 4 {
		t.Fatalf("metrics = %+v, want compromised=4 healthy=12 stepsTaken=4", result.Metrics)
	}
	assertLastEvent(t, result.Events, "attack")
}

func TestAttackDFSCompromisesExpectedOrder(t *testing.T) {
	engine := NewEngine()

	result, err := engine.Attack(AttackDFS, "N0", 4)
	if err != nil {
		t.Fatalf("Attack() error = %v", err)
	}

	want := []string{"N0", "N1", "N2", "N3"}
	if !reflect.DeepEqual(result.Infected, want) {
		t.Fatalf("infected = %v, want %v", result.Infected, want)
	}
}

func TestDirectDefenseBlocksInfection(t *testing.T) {
	engine := NewEngine()

	defense, err := engine.Defend(DefenseDirect, "N1")
	if err != nil {
		t.Fatalf("Defend() error = %v", err)
	}
	if defense.Metrics.DefendedCount != 1 {
		t.Fatalf("defended count = %d, want 1", defense.Metrics.DefendedCount)
	}

	attack, err := engine.Attack(AttackBFS, "N0", 4)
	if err != nil {
		t.Fatalf("Attack() error = %v", err)
	}
	for _, node := range attack.Infected {
		if node == "N1" {
			t.Fatalf("directly defended node N1 was infected: %v", attack.Infected)
		}
	}
	if attack.Metrics.DefendedCount != 1 || attack.Metrics.CompromisedCount != 4 {
		t.Fatalf("metrics = %+v, want defended=1 compromised=4", attack.Metrics)
	}
}

func TestCascadeDefenseBlocksSeedAndNeighbors(t *testing.T) {
	engine := NewEngine()

	defense, err := engine.Defend(DefenseCascade, "N0")
	if err != nil {
		t.Fatalf("Defend() error = %v", err)
	}
	wantDefended := []string{"N0", "N1", "N8", "N15"}
	if !reflect.DeepEqual(defense.Defended, wantDefended) {
		t.Fatalf("defended = %v, want %v", defense.Defended, wantDefended)
	}

	attack, err := engine.Attack(AttackBFS, "N0", 4)
	if err != nil {
		t.Fatalf("Attack() error = %v", err)
	}
	if len(attack.Infected) != 0 {
		t.Fatalf("infected = %v, want none", attack.Infected)
	}
	if attack.Metrics.DefendedCount != 4 || attack.Metrics.CompromisedCount != 0 {
		t.Fatalf("metrics = %+v, want defended=4 compromised=0", attack.Metrics)
	}
}

func TestRollbackRestoresPreviousSnapshots(t *testing.T) {
	engine := NewEngine()

	if _, err := engine.Attack(AttackBFS, "N0", 2); err != nil {
		t.Fatalf("Attack() error = %v", err)
	}
	if _, err := engine.Defend(DefenseDirect, "N3"); err != nil {
		t.Fatalf("Defend() error = %v", err)
	}

	firstRollback, err := engine.Rollback()
	if err != nil {
		t.Fatalf("Rollback() error = %v", err)
	}
	if firstRollback.Metrics.CompromisedCount != 2 || firstRollback.Metrics.DefendedCount != 0 || firstRollback.Metrics.StepsTaken != 2 {
		t.Fatalf("first rollback metrics = %+v, want compromised=2 defended=0 stepsTaken=2", firstRollback.Metrics)
	}
	assertLastEvent(t, firstRollback.Events, "rollback")

	secondRollback, err := engine.Rollback()
	if err != nil {
		t.Fatalf("Rollback() error = %v", err)
	}
	if secondRollback.Metrics.CompromisedCount != 0 || secondRollback.Metrics.DefendedCount != 0 || secondRollback.Metrics.HealthyCount != 16 || secondRollback.Metrics.StepsTaken != 0 {
		t.Fatalf("second rollback metrics = %+v, want all healthy with zero steps", secondRollback.Metrics)
	}
	if len(secondRollback.Events) != 4 {
		t.Fatalf("events length = %d, want 4 append-only events", len(secondRollback.Events))
	}
}

func TestPrimMSTCoversEveryNode(t *testing.T) {
	graph := DefaultGraph()

	edges, err := graph.PrimMST("N0")
	if err != nil {
		t.Fatalf("PrimMST() error = %v", err)
	}
	if len(edges) != 15 {
		t.Fatalf("MST edge count = %d, want 15", len(edges))
	}

	covered := map[string]bool{"N0": true}
	for _, edge := range edges {
		if !covered[edge.From] {
			t.Fatalf("MST edge %v does not grow from covered set", edge)
		}
		covered[edge.To] = true
	}
	if len(covered) != 16 {
		t.Fatalf("MST covered %d nodes, want 16", len(covered))
	}
}

func assertLastEvent(t *testing.T, events []Event, kind string) {
	t.Helper()
	if len(events) == 0 {
		t.Fatalf("events is empty")
	}
	last := events[len(events)-1]
	if last.Kind != kind {
		t.Fatalf("last event kind = %q, want %q", last.Kind, kind)
	}
	if last.TS == "" || last.Detail == "" {
		t.Fatalf("last event missing timestamp or detail: %+v", last)
	}
}
