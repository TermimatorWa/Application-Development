# Application Development – Oostvaardersplassen population model

A JavaFX application that explores how **red deer**, **Heck cattle** and **Konik horses** in the
Oostvaardersplassen could develop under the 1,100-animal management reference, after releasing grey
wolves, and with a change in temperature. It follows section 5 (Methodology) of the project plan
(Group 5). It shows trends and compares scenarios. It does not predict exact animal numbers.

## Research questions

- **A.** Is a population of 1,100 animals (500 deer and 600 cattle and horses) stable?
- **B.** What happens to deer, cattle and horses when grey wolves are released, depending on how
  many there are? *(main focus)*
- **C.** What is the effect of a rise in temperature, and how much warming would force a policy change?

## Mathematical model

```
dXᵢ/dt = rᵢ Xᵢ (1 − Σⱼ αᵢⱼ Xⱼ / Kᵢ) − W fᵢ           herbivore i = deer, cattle, horses
fᵢ     = a pᵢ Xᵢ / (1 + a h Σⱼ pⱼ Xⱼ)               kills per wolf per year (saturating)
dW/dt  = e W Σᵢ fᵢ − q W                           only when wolf numbers may change
Kᵢ     = Kᵢ,₀ × grass availability × GP(t + ΔT) / GP(t)
GP(t)  = exp[−0.5 ((t − 20) / var)²]               Appendix 3, C3 grass, t₀ = 20 °C
```

| Symbol | Meaning |
|--------|---------|
| `Xᵢ` | number of animals of herbivore *i* |
| `rᵢ` | intrinsic growth rate (per year) |
| `Kᵢ` | carrying capacity |
| `αᵢⱼ` | competition for grass: effect of species *j* on species *i* |
| `W` | number of wolves (`W₀` = number released, held constant by default) |
| `a`, `h` | wolf attack rate and handling time |
| `pᵢ` | wolf preference for prey *i* (deer = 1) |
| `e`, `q` | wolf conversion efficiency and mortality |
| `t`, `ΔT` | baseline average temperature and temperature change |

Logistic growth is the benchmark (Gotelli, ch. 2). Competition for grass (ch. 5) and the
Lotka-Volterra predator-prey interaction (ch. 6, Predation) are the extensions. The equations are
solved with fourth-order Runge-Kutta and reported once a year.

All parameter values are **illustrative assumptions** (see `ModelParameters`). The central herbivore
values were chosen so that the model without wolves follows the broad 1992–2017 pattern of the
teaching dataset (see the Validation tab).

## The application (project plan 5.2)

| Plan item | Where in the app |
|-----------|------------------|
| Change wolf numbers, starting populations and key assumptions | Left panel: `W₀`, deer/cattle/horses (presets for the 1,100 reference and the 2025 counts), low/central/high assumptions, temperature, horizon |
| Subquestion 1 – baseline stability with zero wolves | **Populations** tab: three curves + total vs. the 1,100 line, with the stability test (persistence, bounded fluctuations, no sustained decline) and editable criteria |
| Subquestion 2 – logistic growth, competition, predator-prey | `PopulationModel` |
| Subquestion 3 – wolf grid 0, 2, 4, 6, 8, 10 (adjustable), constant or changing wolves | **Wolf scenarios** tab: curves per `W₀`, and a table with each herbivore, total, minimum population and time to threshold crossings |
| Subquestion 4 – low/central/high for prey preference, attack rate, grass, growth | **Sensitivity** tab: outcome per `W₀` for every assumption, and whether the ranking of wolf scenarios changes |
| Subquestion 5 – temperature via the Appendix 3 GP curve | **Temperature scenarios** tab: GP curve, total per `ΔT`, removal needed to hold 1,100, and flags for possible policy review |
| Observed vs. simulated, with years held back for checking | **Validation** tab |
| Short explanation of limitations | **About & limitations** tab |
| Basic tests (non-negative, `W₀ = 0`, parameter limits) | `src/test/java` |

## Observed data

The teaching dataset is for educational use only, so it is **not** in the repository. To use the
Validation tab, export the sheet *Number of large herbivores in the Oostvaardersplassen 1983–2025*
to CSV with the columns `Year, Heck cattle, Konik horses, Red deer`. Then either save it as
`data/herbivores.csv` (this folder is git-ignored) or load it with the button in the tab.

## Project structure

```
src/main/java/com/example/app/
├── App.java                     # starts JavaFX
├── model/
│   ├── PopulationModel.java     # the equations and the RK4 solver
│   ├── ModelParameters.java     # parameter values and limits
│   ├── Assumptions.java         # low / central / high choices
│   ├── Climate.java, GrassGrowth.java
│   ├── Scenario.java, SimulationResult.java, PopulationState.java
│   ├── ScenarioMetrics.java     # minima, threshold crossings, stability test
│   ├── ReferenceBalance.java    # removal needed to hold 1,100
│   ├── Reference.java, Species.java, AssumptionLevel.java
│   └── AnimalPopulation.java, BirthRate.java, DeathRate.java, GrowthRate.java,
│       ExponentialGrowth.java, Deer.java, Cattle.java, Horses.java   # first model (b, d, r)
├── data/ObservedCounts.java     # reads the CSV counts
└── ui/                          # control panel and one class per tab
```

## Requirements

- Java 25
- Maven 3.9+
- JavaFX 25 (downloaded automatically by Maven)

## Build, test and run

```
mvn test
mvn javafx:run
```
