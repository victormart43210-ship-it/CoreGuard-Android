# Quilla Cyber Codex — sources

On-device defensive education for Ultimate Quilla. No cloud LLM.

| Corpus | Origin |
| --- | --- |
| `mitre-attack-mobile.json` | Technique IDs/names from [MITRE ATT&CK Mobile STIX](https://github.com/mitre-attack/attack-stix-data); descriptions condensed for device size |
| `owasp-masvs.json` | Educational summaries aligned to [OWASP MASVS](https://mas.owasp.org/MASVS/) categories |
| `pentest-methodology.json` | Authorized-testing methodology (ROE, phases, surfaces) — defensive framing only |
| `incident-response.json` | NIST-style IR phases + mobile triage orientation |
| `android-hardening.json` | Android permission, update, and network hygiene |
| `threats.json` / `network-crypto.json` | Common mobile threats and TLS/DNS defender notes |
| `emerging-mobile-attacks.json` | Defensive briefs on publicly documented mobile attack methods (overlays, sideload droppers, deep links, spyware paths, DNS C2, MASTG/WSTG) |
| `malware-vuln-infinity-training.json` | Quilla Infinity pedagogy — uncapped angel/swarm study of malware + vulnerability corpora |

## Runtime Quilla Infinity Intel (optional HTTPS)

| Source | Use |
| --- | --- |
| Amnesty Tech / MVT STIX2 campaigns | IOC correlation (Research / sliding window) |
| f00wl stalkerware STIX2 | Stalkerware domain/package IOCs |
| CISA KEV JSON | Android/mobile-relevant known-exploited CVEs → Cyber Codex (uncapped) |
| MISP Android galaxy | Malware family defensive briefs → Cyber Codex (uncapped) |
| MISP Malpedia galaxy (mobile filter) | Broader evolving malware-family briefs → Cyber Codex |
| `QuillaInfinityTrainer` | Assigns dossiers to angels + notifies swarm (on-device; not cloud LLM) |

Quilla refuses unauthorized offensive how-to requests (`QuillaEthicsGuard`).
