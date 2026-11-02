# Auction Mod (Fabric, Minecraft 26.2)

A client-side mod for running auctions on Donut SMP.

1. Press **Right Shift** (or type `/fauction`) to open the menu.
2. **Item** - click an item (search / filter by Blocks or Items) or press *Use held item*.
3. **Price** - set the minimum bid (`1m`, `500k`, `2.5b` ...) and how many items you're selling.
4. **Time** - pick a duration (1 min ... 24 hours) or type your own in minutes. Choose whether to announce in chat.
5. **Start Auction.** A box appears top-left showing the item, highest bid, who bid and time left.

The mod reads the server's `Player paid you $5M` chat lines. A payment counts only if it is **at least the minimum bid**, and it becomes the highest bid only if it beats the current one.

## Building the .jar (easiest: GitHub)
Upload this folder to a GitHub repo (including the hidden `.github` folder), open the **Actions** tab, wait for the green tick, and download the `donutauction-jar` artifact. Unzip it - the real mod is the `.jar` inside.

Building on your own PC needs **Java 25**: run `gradlew.bat build` (Windows) or `./gradlew build`, and take `build/libs/donutauction-1.0.0.jar`.

## Installing
Put the mod jar and **Fabric API for 26.2** in your `mods` folder, and launch the Fabric 26.2 profile.

## Commands
| Command | What it does |
|---|---|
| `/fauction` | Open the menu |
| `/fauction cancel` | Cancel the running auction |
| `/fauction test <name> <amount>` | Pretend someone paid you, e.g. `/fauction test Steve 5m` |
| `/fauction debug` | Shows chat lines containing "paid" that the mod didn't understand |

If bids aren't detected, run `/fauction debug`, get paid once, and adjust the `PAID_YOU` pattern in `AuctionManager.java`.
