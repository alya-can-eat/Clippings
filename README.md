# Clippings
A Plugin that enables collecting tall grass, fern, dead bushes and cobwebs with shears for Poseidon Server Minecraft b1.7.3

---

> [!WARNING]
> This plugin was vibecoded but seems to work good.

---

In Beta 1.7.3, shears only collect leaves and wool. Tall grass, ferns, dead bushes and cobwebs can't be obtained in survival: grass only drops seeds now and then, dead bushes drop nothing, and cobwebs only drop string. Since cobwebs don't generate they remain unobtainable. A separate plugin is needed for cobwebs to be made accessible.

This plugin lets shears collect them as blocks, like in later Minecraft versions.

## Features

- **Shears collect**:
  - Tall grass (`31:1`)
  - Fern (`31:2`)
  - Dead shrub (`31:0`, the rare dead-bush-looking variant of tall grass)
  - Dead bush (`32`)
  - Cobweb (`30`)
- **Shears lose 1 durability** per block, like on leaves. They break when used up.
- **Vanilla otherwise**: breaking by hand still drops seeds now and then, and other tools behave as before.

## Requirements

- Project Poseidon (Beta 1.7.3 Server - Fork of CB1060)
  - https://github.com/retromcorg/Project-Poseidon
- Java 8

Only tested with Project Poseidon. It may work on CraftBukkit 1060, but that is untested.

## Installation

1. Put `Clippings*.jar` into the `plugins/` folder.
2. Start the server. `plugins/Clippings/config.yml` is created on the first start.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `clippings.use` | everyone | Collect tall grass, ferns, dead bushes and cobwebs with shears |

## Configuration

`plugins/Clippings/config.yml`

| Key | Default | Description |
|---|---|---|
| `collect-tall-grass` | `true` | Shears collect tall grass, ferns and dead shrubs |
| `collect-dead-bush` | `true` | Shears collect dead bushes |
| `collect-cobweb` | `true` | Shears collect cobwebs instead of string |
| `fix-tall-grass-inventory` | `true` | Keep grass, fern and shrub items apart and place the right variant (see below) |

## How the tall grass fix works

Beta 1.7.3 registers the item of block 31 as a plain block item without subtypes. Grass, fern and shrub items would merge into one stack, and every one of them would place as a dead shrub.

On start, the plugin replaces this item in memory with one that has subtypes, the same way vanilla handles leaves. On stop, the original item is put back.

- **World save**: nothing changes. Items are saved with their data value anyway. If you remove the plugin, the items stay, but they place as dead shrubs again.
- **Other plugins**: if another plugin has already replaced item 31, the fix is skipped and a warning is logged.

## Notes

- **Inventory icon**: the vanilla client shows the fern item with the grass icon. Dead shrub and grass have their own icons. Placed ferns look correct. This can't be changed from the server.
- **Inventory sync**: the client doesn't know that grass and fern are separate items and would merge them when clicking. Every inventory click with tall grass in the slot or on the cursor makes the server resend the whole window, using the vanilla resync path. Very fast clicking right after such a click can be ignored once, like vanilla does on a desync.
- **Logging plugins**: the vanilla drop (seeds or string) is removed and the block is dropped instead. The break itself is a normal, uncancelled `BlockBreakEvent`.

## Building from source

Compile against the Project Poseidon server jar (Java 8 target):

```bash
javac --release 8 -cp poseidon.jar -d build src/ch/alya/clippings/*.java
cp plugin.yml build/
jar cf Clippings.jar -C build .
```
