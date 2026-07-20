package sim

import "time"

// Event is an append-only simulation audit entry.
type Event struct {
	TS     string `json:"ts"`
	Kind   string `json:"kind"`
	Detail string `json:"detail"`
}

func newEvent(kind, detail string) Event {
	return Event{
		TS:     time.Now().UTC().Format(time.RFC3339Nano),
		Kind:   kind,
		Detail: detail,
	}
}
