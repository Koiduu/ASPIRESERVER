# ASPIRESERVER

Custom PaperMC 1.21.11 server network plugin suite with strict anti-griefing, zero-lag performance optimization, and seamless mod integration.

## Modules

| Module | Description |
|--------|-------------|
| **aspire-core** | Global network commands: Party, Friends, Admin tools, Staff chat, Lobby |
| **aspire-buildbattle** | Competitive Build Battle minigame with 4 modes, voting, anti-grief |
| **aspire-smp** | Survival plugin with land claims, graveyard system, one-player sleep |
| **aspire-creative** | Creative plot world with Small/Medium/Large tiers, async operations |

## Requirements

- Java 21+
- PaperMC 1.21.4+ (targets 1.21.11 runtime)
- Maven 3.6+

## Build

```bash
mvn clean package
```

Output JARs will be in each module's `target/` directory.

## Installation

1. Build all modules
2. Place all 4 JARs in your server's `plugins/` folder
3. **aspire-core** must load first (other plugins depend on it)
4. Restart server
5. Configure each plugin via their `config.yml`

## Optional Dependencies

- **FastAsyncWorldEdit** - Required for Pro modes in Build Battle and large operations in Creative
- **WorldEdit** - Alternative to FAWE
- **AxiomPaper** - For Axiom mod integration in Pro modes

## Permissions

See each module's `plugin.yml` for full permission nodes. Key admin permission: `aspire.admin.bypass`
