# ❄️ IceBoatRacing

**IceBoatRacing** is a Minecraft **Paper** plugin built for racing servers.
It provides a full race system with **checkpoints**, **live scoreboards**, and even **custom cars/vehicles**.

Supports multiple languages and is designed for competitive Ice Boat Racing gameplay.

---

## 🚀 Features

* 🏁 **Race System**

  * Create and manage races
  * Track player race progress
  * Store race data during gameplay

* 📍 **Checkpoint Management**

  * Create checkpoints on tracks
  * Detect when players pass checkpoints
  * Required for race completion

* 📊 **Live Scoreboard**

  * Real-time race tracking
  * Live updates during races

* 🚗 **Car / Vehicle Support**

  * Boats are used as car - [recommended pack](https://modrinth.com/resourcepack/f1-2026-boat)
  * Car spawning and management
  * Car-specific listener + manager system

* 🌍 **Multi-Language Messages**

  * English (`en_US.yml`)
  * French (`fr_FR.yml`)

* ⚙️ Configurable Plugin Setup

  * Built-in config system (`config.yml`)
  * Paper plugin metadata (`paper-plugin.yml`)

---

## 📦 Installation

1. Download the plugin `.jar` from the **Releases** section

2. Place it into your server folder:

   plugins/

3. Restart the server

---

## ⚙️ Requirements

* Java **17+**
* Paper / Spigot **1.21+**

---

## 🎮 Commands

### 🏁 Race Commands

| Command | Description       |
| ------- | ----------------- |
| `/race` | Main race command |

Handled by: `RaceCommand`, `RaceManager`

---

### 📍 Checkpoint Commands

| Command       | Description             |
| ------------- | ----------------------- |
| `/checkpoint` | Manage race checkpoints |

Handled by: `CheckpointCommand`, `CheckpointManager`

---

### 🚗 Car Commands

| Command | Description          |
| ------- | -------------------- |
| `/car`  | Spawn or manage cars |

Handled by: `CarCommand`, `CarManager`

---

### 🔧 Main Plugin Command

| Command | Description                |
| ------- | -------------------------- |
| `/ibr`  | Main IceBoatRacing command |

Handled by: `IBRCommand`

---

## 🗂️ Project Structure

src/main/java/fr/mattmunich/iceBoatRacing<br/>
├── cars → Vehicle system<br/>
├── race → Race logic and listeners<br/>
├── livescoreboard/checkpoint → Checkpoint + scoreboard system<br/>
├── listeners → Player connection handling<br/>
└── Main.java → Plugin entry point<br/>

---

## 🌍 Languages

Language files are stored in:

src/main/resources/lang/

* `en_US.yml`
* `fr_FR.yml`

---

## 🧱 Building from Source

This project uses **Maven**.

Build the plugin using:

mvn clean package

The compiled jar will appear in:

target/

---

## 🤝 Contributing

Pull requests are welcome!

1. Fork the repository
2. Create a new branch
3. Submit a PR with improvements or fixes

---
