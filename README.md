# 🧟 Horde Apocalypse

<div align="center">

![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green.svg)
![Fabric](https://img.shields.io/badge/Fabric_Loader-0.19.3-orange.svg)
![Java](https://img.shields.io/badge/Java-25-red.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

*Un mod Minecraft Fabric inspiré de 7 Days to Die*

[Installation](#-installation) • [Fonctionnalités](#-fonctionnalités) • [Configuration](#️-configuration) • [Commandes](#️-commandes)

</div>

---

## 🎮 Présentation

Horde Apocalypse transforme Minecraft en une expérience de survie progressive
inspirée de 7 Days to Die. Tous les 7 jours, **à la tombée de la nuit**, une horde
déferle sur votre base. Et pendant 100 jours, tous les monstres du jeu deviennent
inexorablement plus dangereux.

## ✨ Fonctionnalités

### 🌙 Système de Hordes (tous les 7 jours)

- **Déclenchement au crépuscule** : la horde apparaît quand le soleil se couche
  (temps du monde `13000`), pas à l'aube — sans quoi les morts-vivants
  s'enflammeraient avant même de vous atteindre.
- **Spawn** : à ~200 blocs de chaque joueur. Si vous êtes terré sous terre, la
  horde vient vous chercher à votre profondeur.
- **Composition** (par groupe de joueurs) :
  - 30 Zombies
  - 20 Squelettes (avec leur arc)
  - 10 Creepers chargés ⚡
- **Durée** : toute la nuit. Au lever du soleil, la horde se dissipe — et le
  soleil se charge des retardataires.
- **Nettoyage automatique** : les mobs de horde restants sont supprimés, y
  compris ceux qui traînaient dans des chunks déchargés.

### 🔨 Capacité de casse des blocs

Les monstres de la horde — zombies, squelettes, creepers, pillagers et wardens —
creusent vers vous :

| Jour | Blocs cassables |
|------|----------------|
| **1+** | 🌲 Tout le bois (planches, rondins, portes, clôtures, escaliers, dalles…) |
| **50+** | 🪨 Toute la maçonnerie (pierre, briques, deepslate, murs, escaliers…) |
| **100+** | 💠 Les métaux (fer, diamant, or, émeraude, porte en fer) |

**Toujours indestructibles** : bedrock, obsidienne, netherite, ancient debris,
deepslate renforcé, cadres de portail de l'End, générateurs de monstres — ainsi
que **tout bloc contenant un inventaire** (coffres, fourneaux, barils).

### 📈 Difficulté progressive (100 jours)

**Tous les monstres du jeu** gagnent progressivement :

- 💚 **Santé** : +3 % par jour, jusqu'à **+300 %** (×4) au jour 100
- ⚔️ **Attaque** : +3 % par jour, jusqu'à **+300 %** (×4) au jour 100
- 🏃 **Vitesse** : +1,5 % par jour, jusqu'à **+150 %** (×2,5) au jour 100

> Les bonus sont appliqués sous forme de modificateurs d'attributs recalculés à
> chaque chargement du monstre : ils ne se cumulent jamais d'une sauvegarde à
> l'autre, et les monstres déjà en vie continuent de progresser.
>
> Certains monstres (squelettes, creepers, Wither) n'ont pas d'attribut
> « dégâts d'attaque » — leurs dégâts viennent de projectiles ou d'explosions.
> Le bonus d'attaque ne s'y applique donc pas ; santé et vitesse, si.

### 🔥 Apocalypse finale (jour 100)

À partir du jour 100 — **et le jour 100 lui-même est toujours une nuit de
horde**, même s'il n'est pas un multiple de 7 — chaque horde s'accompagne de :

- **2× Wardens** 👾
- **3× Withers** 💀
- **50× Pillagers** 🎯 (avec leur arbalète)

Ces boss n'apparaissent **qu'une seule fois par horde**, quel que soit le nombre
de groupes de joueurs sur le serveur.

## 📦 Installation

### Prérequis

- Minecraft Java Edition **26.2**
- [Fabric Loader](https://fabricmc.net/use/) **0.19.3+**
- [Fabric API](https://modrinth.com/mod/fabric-api) **0.158.0+**
- **Java 25**

### Étapes

1. Téléchargez le `.jar` depuis [Releases](https://github.com/Kyuwei/HordeApocalypse/releases)
2. Placez-le dans le dossier `mods/`
3. Lancez le jeu avec le profil Fabric

## ⚙️ Configuration

`config/hordeapocalypse.json` est généré au premier lancement.

```json
{
  "hordeDayInterval": 7,
  "hordeSpawnDistance": 200,
  "hordeZombieCount": 30,
  "hordeSkeletonCount": 20,
  "hordeCreeperCount": 10,
  "hordeStartTimeOfDay": 13000,

  "woodBreakStartDay": 1,
  "stoneBreakStartDay": 50,
  "hardBreakStartDay": 100,
  "blockBreakSpeed": 0.1,
  "breakDropsItems": false,
  "maxBlockBreaksPerTick": 8,

  "maxDifficultyDay": 100,
  "maxHealthMultiplier": 4.0,
  "maxDamageMultiplier": 4.0,
  "maxSpeedMultiplier": 2.5,

  "finalDayWardenCount": 2,
  "finalDayWitherCount": 3,
  "finalDayPillagerCount": 50,

  "maxConcurrentHordeMobs": 300,
  "clusterMergeDistance": 100,
  "maxSpawnsPerTick": 20
}
```

### Paramètres détaillés

| Paramètre | Description | Défaut |
|------------|-------------|--------|
| `hordeDayInterval` | Fréquence des hordes, en jours | 7 |
| `hordeSpawnDistance` | Distance de spawn, en blocs | 200 |
| `hordeStartTimeOfDay` | Heure du monde à laquelle la horde déferle (13000 = crépuscule) | 13000 |
| `woodBreakStartDay` | Jour où la horde commence à casser le bois | 1 |
| `stoneBreakStartDay` | Jour où elle s'attaque à la pierre | 50 |
| `hardBreakStartDay` | Jour où elle perce les métaux | 100 |
| `blockBreakSpeed` | Fraction de bloc cassée par tick (0.1 = 10 ticks, soit 0,5 s par bloc, quelle que soit la dureté) | 0.1 |
| `breakDropsItems` | Les blocs cassés lâchent-ils leurs items | `false` |
| `maxBlockBreaksPerTick` | Plafond global de blocs détruits par tick, toutes hordes confondues | 8 |
| `maxHealthMultiplier` | Multiplicateur **total** de santé au jour 100 (4.0 = +300 %) | 4.0 |
| `maxDamageMultiplier` | Multiplicateur **total** d'attaque au jour 100 (4.0 = +300 %) | 4.0 |
| `maxSpeedMultiplier` | Multiplicateur **total** de vitesse au jour 100 (2.5 = +150 %) | 2.5 |
| `maxConcurrentHordeMobs` | Plafond de mobs par horde, tous groupes confondus | 300 |
| `clusterMergeDistance` | En deçà de cette distance, deux joueurs partagent une seule horde | 100 |
| `maxSpawnsPerTick` | Mobs matérialisés par tick, pour lisser le pic de lag | 20 |

> Les valeurs hors bornes sont automatiquement ramenées dans le domaine valide,
> journalisées, puis réécrites dans le fichier. Un fichier illisible est
> renommé en `.bak` et régénéré.

## 🛠️ Commandes

Réservées aux opérateurs (niveau « gamemasters ») :

| Commande | Description |
|----------|-------------|
| `/hordeapocalypse force` | Déclenche immédiatement une horde autour de vous |
| `/hordeapocalypse stop` | Termine la horde en cours et nettoie ses mobs |
| `/hordeapocalypse status` | Jour courant, état de la horde, mobs suivis et en file |
| `/hordeapocalypse day <n>` | Force le jour de survie (test et support) |

> **À savoir** : le compteur de jours du mod se base sur le temps de jeu écoulé,
> qui ne peut être ni rembobiné ni gelé. Il peut donc s'écarter de l'horloge
> visible si les joueurs dorment souvent ou si un opérateur utilise `/time set`.
> `/hordeapocalypse day <n>` permet de le réaligner.

## 🛠️ Compilation depuis les sources

```bash
git clone https://github.com/Kyuwei/HordeApocalypse.git
cd HordeApocalypse
./gradlew build      # le .jar se trouve dans build/libs/
./gradlew test       # tests unitaires de la configuration
```

## 📝 Compatibilité

- ✅ Compatible avec la plupart des mods Fabric
- ✅ Fonctionne en multijoueur et sur serveur dédié
- ⚠️ Cible **Minecraft 26.2 uniquement** — aucune rétrocompatibilité avec les
  versions 1.21.x et antérieures

## 🐛 Bugs & Suggestions

Ouvrez une [issue](https://github.com/Kyuwei/HordeApocalypse/issues).

## 📜 Licence

MIT. Voir [LICENSE](LICENSE).

## 👤 Auteur

Créé par [Kyuwei](https://github.com/Kyuwei)

---

<div align="center">

**Bon courage, survivant !** 🧟‍♂️

*N'oubliez pas de renforcer votre base avant la 7ème nuit...*

</div>
