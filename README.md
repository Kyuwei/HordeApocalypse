# 🧟 Horde Apocalypse

<div align="center">

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-green.svg)
![Fabric](https://img.shields.io/badge/Fabric-0.18.1-orange.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

*Un mod Minecraft Fabric inspiré de 7 Days to Die*

[Installation](#installation) • [Fonctionnalités](#fonctionnalités) • [Configuration](#configuration) • [Documentation](https://notion.so)

</div>

---

## 🎮 Présentation

Horde Apocalypse transforme Minecraft en une expérience de survie progressive inspirée de 7 Days to Die. Affrontez des hordes de monstres de plus en plus puissants tous les 7 jours, alors que la difficulté augmente graduellement sur 100 jours.

## ✨ Fonctionnalités

### 🌙 Système de Hordes (Tous les 7 jours)

- **Spawn automatique** : Une horde spawn à ~200 blocs de chaque joueur
- **Composition** :
  - 30 Zombies
  - 20 Squelettes  
  - 10 Creepers chargés (⚡)
- **Durée** : 1 jour Minecraft (20 minutes)
- **Nettoyage automatique** : Les mobs disparaissent après la horde

### 🔨 Capacité de Casse des Blocs

Les monstres de la horde peuvent casser des blocs :

| Jour | Blocs cassables |
|------|----------------|
| **1+** | 🌲 Bois (planches, portes, clôtures, rondins) |
| **50+** | 🪨 Pierre (pierre, cobblestone, briques) |
| **100+** | 💠 Blocs durs (fer, diamant, or, émeraude) |

**Indestructibles** : Bedrock, Obsidienne, Netherite

### 📈 Difficulté Progressive (100 jours)

**Tous les monstres du jeu** gagnent progressivement :

- 💚 **Santé** : +3% par jour (max +300%)
- ⚔️ **Attaque** : +3% par jour (max +300%)
- 🏃 **Vitesse** : +1.5% par jour (max +150%)

### 🔥 Apocalypse Finale (Jour 100)

Le 100ème jour, en plus de la horde normale :

- **2x Wardens** 👾
- **3x Withers** 💀
- **50x Pillagers** 🎯

## 📦 Installation

### Prérequis

- Minecraft Java Edition **1.21.5+** (testé sur **1.21.11**)
- [Fabric Loader](https://fabricmc.net/use/) **0.18.0+**
- [Fabric API](https://modrinth.com/mod/fabric-api) **0.110.5+**

### Étapes

1. Téléchargez le fichier `.jar` depuis [Releases](https://github.com/Kyuwei/HordeApocalypse/releases)
2. Placez-le dans le dossier `mods/` de votre installation Minecraft
3. Lancez le jeu avec le profil Fabric

## ⚙️ Configuration

Le fichier de configuration `hordeapocalypse.json` est généré automatiquement dans le dossier `config/` au premier lancement.

### Paramètres disponibles

```json
{
  "hordeDayInterval": 7,
  "hordeSpawnDistance": 200,
  "hordeZombieCount": 30,
  "hordeSkeletonCount": 20,
  "hordeCreeperCount": 10,

  "woodBreakStartDay": 1,
  "stoneBreakStartDay": 50,
  "hardBreakStartDay": 100,
  "blockBreakSpeed": 0.1,
  "breakDropsItems": false,

  "maxDifficultyDay": 100,
  "maxHealthMultiplier": 3.0,
  "maxDamageMultiplier": 3.0,
  "maxSpeedMultiplier": 1.5,

  "finalDayWardenCount": 2,
  "finalDayWitherCount": 3,
  "finalDayPillagerCount": 50,

  "maxConcurrentHordeMobs": 300,
  "clusterMergeDistance": 100
}
```

### Paramètres détaillés

| Paramètre | Description | Valeur par défaut |
|------------|-------------|-------------------|
| `hordeDayInterval` | Fréquence des hordes (en jours) | 7 |
| `hordeSpawnDistance` | Distance de spawn (en blocs) | 200 |
| `woodBreakStartDay` | Jour de début casse du bois | 1 |
| `stoneBreakStartDay` | Jour de début casse de la pierre | 50 |
| `hardBreakStartDay` | Jour de début casse blocs durs | 100 |
| `blockBreakSpeed` | Vitesse de casse (0.1 = lent) | 0.1 |
| `maxHealthMultiplier` | Multiplicateur max de santé | 3.0 (300%) |
| `maxDamageMultiplier` | Multiplicateur max d'attaque | 3.0 (300%) |
| `maxSpeedMultiplier` | Multiplicateur max de vitesse | 1.5 (150%) |
| `breakDropsItems` | Les blocs cassés laissent des drops | `false` |
| `maxConcurrentHordeMobs` | Plafond global de mobs spawnés par horde | 300 |
| `clusterMergeDistance` | Distance pour fusionner les hordes de joueurs proches | 100 |

## 🛠️ Commandes (admin, perm level 2)

| Commande | Description |
|----------|-------------|
| `/hordeapocalypse force` | Démarre immédiatement une horde sur le joueur appelant |
| `/hordeapocalypse stop` | Termine la horde en cours et nettoie les mobs taggés |
| `/hordeapocalypse status` | Affiche le jour courant, l'état de la horde et le nombre de mobs |

## 🛠️ Compilation depuis les sources

```bash
# Cloner le repository
git clone https://github.com/Kyuwei/HordeApocalypse.git
cd HordeApocalypse

# Compiler le mod
./gradlew build

# Le fichier .jar sera dans build/libs/
```

## 📝 Compatibilité

- ✅ Compatible avec la plupart des mods Fabric
- ✅ Fonctionne en multijoueur
- ✅ Compatible avec les serveurs dédiés

## 🐛 Bugs & Suggestions

Ouvrez une [issue](https://github.com/Kyuwei/HordeApocalypse/issues) pour signaler un bug ou proposer une fonctionnalité.

## 📜 Licence

Ce projet est sous licence MIT. Voir [LICENSE](LICENSE) pour plus de détails.

## 👤 Auteur

Créé par [Kyuwei](https://github.com/Kyuwei)

---

<div align="center">

**Bon courage, survivant !** 🧟‍♂️

*N'oubliez pas de renforcer votre base avant le 7ème jour...*

</div>