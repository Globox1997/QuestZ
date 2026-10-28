# QuestZ
QuestZ enhances the advancement system of minecraft for a simple quest system.

### Installation
QuestZ is a mod built for the [Fabric Loader](https://fabricmc.net/). It requires [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) and [Cloth Config API](https://www.curseforge.com/minecraft/mc-mods/cloth-config) to be installed separately; all other dependencies are installed with the mod.

### License
QuestZ is licensed under MIT.

### Quest files

Quests are usually created with the in-game editor, but they can also be written by hand as JSON files.  
Quests are at ```WORLDFOLDER\questz\chapters\CHAPTERID.json```

- Texts like `title` and `description` can be a plain string like `"Wood"`, a translation key like `{"translate": "quest.mymod.wood"}` or any other text component.  
- Items are written like `{"id": "minecraft:diamond_sword"}`. Components can be added like `{"id": "minecraft:diamond_sword", "components": {"minecraft:damage": 10}}`.  
- Fields for blocks, entities, items, biomes and structures accept one id, a tag starting with `#` like `"#minecraft:logs"` or a list like `["minecraft:zombie", "minecraft:husk"]`.

#### Modpacks
To ship quests with a modpack, put the chapter files into ```config\questz\default\```
Every new world copies them into its own `questz\chapters` folder on the first start, after that the world keeps its own copy which can be edited in-game.

#### Chapter
A chapter requires:
- id (string): the chapter id, should be the same as the file name

A chapter can include:
- title (text): the name of the tab
- icon (item): the icon of the tab, default is a book
- order (integer): tabs are sorted by this number, default is 0
- background (string): a texture like `minecraft:textures/block/stone.png`
- line_style (string): `direct` (default) or `orthogonal` for vanilla like lines
- quests: the list of quests in this chapter

#### Quest
A quest requires:
- id (string): the quest id

A quest can include:
- title (text): the name of the quest
- description (list of texts): one entry per paragraph
- icon (item or list of items): several items take turns every second. Without an icon, the icon of the first task is used
- x, y (decimal): the position on the map, one unit is one quest slot
- size (decimal): node size between 0.25 and 4, default is 1
- shape (string): `task` (default), `goal` or `challenge`
- dependencies (list of strings): ids of quests which have to be completed first, they can be in other chapters
- dependency_mode (string): `all` (default) or `any` of the dependencies
- visibility (string): when players can see the quest
  - `always` (default): always visible
  - `dependencies_visible`: visible when all dependencies are visible
  - `dependencies_done`: visible when the dependencies are completed
  - `secret`: hidden until the quest is completed
- tasks: the list of tasks
- task_mode (string): `all` (default) tasks or `any` task has to be done
- rewards: the list of rewards
- repeatable (boolean): the quest restarts after all rewards got claimed, default is false
- auto_claim (boolean): rewards get claimed automatically, default is false

#### Tasks
Every task requires an `id` and a `type`. Tasks with a `count` default to 1.
- `questz:checkmark`: the player confirms it with a button
- `questz:item`: own items
  - item (item or list of items): only the given components get checked
  - tag (string or list of strings): item tags like `#c:glass_blocks`
  - count (integer), consume (boolean): when true, the items have to be handed in with the submit button
- `questz:kill`: entity (optional, any mob if missing), count
- `questz:mine`: block (optional), count
- `questz:place`: block (optional), count
- `questz:craft`: item (optional), count
- `questz:use`: item (optional), count, for example eating or throwing
- `questz:stat`: stat (a custom statistic like `minecraft:jump`), count
- `questz:location`: dimension (default `minecraft:overworld`), pos like `[0, 64, 0]`, radius (default 8)
- `questz:dimension`: dimension like `minecraft:the_nether`
- `questz:biome`: biome
- `questz:structure`: structure, for example `#minecraft:village`
- `questz:advancement`: advancement like `minecraft:story/mine_stone`
- `questz:criterion`: criterion written like an advancement criterion with `trigger` and `conditions`, count

#### Rewards
Every reward requires an `id` and a `type`. All rewards accept `team_reward` (boolean): when true, the reward is given once per team instead of once per player.
- `questz:item`: item, count
- `questz:xp`: amount, levels (boolean, levels instead of points)
- `questz:command`: command, runs as the player with permission level 2
- `questz:loot_table`: loot_table like `minecraft:chests/simple_dungeon`
- `questz:choice`: options (list of items with `count`), the player picks one
- `questz:random`: entries (list of `item` with `count` and `weight`), rolls (default 1)

```json
{
  "id": "basics",
  "title": "Basics",
  "icon": {
    "id": "minecraft:wooden_axe"
  },
  "order": 0,
  "line_style": "orthogonal",
  "quests": [
    {
      "id": "start",
      "title": "Getting Started",
      "description": [
        "Welcome! Click the checkmark task to begin."
      ],
      "x": 0.0,
      "y": 0.0,
      "tasks": [
        {
          "id": "t1",
          "type": "questz:checkmark"
        }
      ],
      "rewards": [
        {
          "id": "r1",
          "type": "questz:item",
          "item": {
            "id": "minecraft:apple"
          },
          "count": 3
        }
      ]
    },
    {
      "id": "glass",
      "title": "Colorful",
      "icon": [
        {
          "id": "minecraft:red_stained_glass"
        },
        {
          "id": "minecraft:blue_stained_glass"
        }
      ],
      "x": 3.0,
      "y": 0.0,
      "shape": "goal",
      "dependencies": [
        "start"
      ],
      "tasks": [
        {
          "id": "t1",
          "type": "questz:item",
          "tag": "#c:glass_blocks",
          "count": 16,
          "consume": true
        },
        {
          "id": "t2",
          "type": "questz:kill",
          "entity": [
            "minecraft:zombie",
            "minecraft:husk"
          ],
          "count": 5
        }
      ],
      "task_mode": "any",
      "rewards": [
        {
          "id": "r1",
          "type": "questz:xp",
          "amount": 5,
          "levels": true,
          "team_reward": true
        },
        {
          "id": "r2",
          "type": "questz:choice",
          "options": [
            {
              "id": "minecraft:diamond_sword",
              "count": 1
            },
            {
              "id": "minecraft:diamond_pickaxe",
              "count": 1
            }
          ]
        }
      ]
    }
  ]
}
```

### Commands
`/questz reload`
- Load all quest files of the world again

`/questz import default`
- Replace the quest book of the world with the default quest book from `config\questz\default`

`/questz quest complete quest [player]` and `/questz quest reset quest [player]`
- Complete or reset one quest for the team of the player

`/questz quest completeall [players]` and `/questz quest resetall [players]`
- Complete or reset all quests for the teams of the players
