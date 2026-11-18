package com.example.app.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;

/** Short explanation of the model and its limitations, as required by plan 5.2. */
final class LimitationsTab extends Tab {

    LimitationsTab() {
        super("About & limitations");
        setClosable(false);
        VBox box = new VBox(10,
                Controls.heading("What this application does"),
                text("It explores how red deer, Heck cattle and Konik horses in the Oostvaardersplassen could "
                        + "develop under the 1,100-animal management reference (question A), after releasing "
                        + "different numbers of grey wolves (question B, main focus) and with a change in "
                        + "temperature (question C). It shows trends and compares scenarios; it does not "
                        + "predict exact future animal numbers."),
                Controls.heading("Model"),
                text("dXᵢ/dt = rᵢ Xᵢ (1 − Σⱼ αᵢⱼ Xⱼ / Kᵢ) "
                        + "− W fᵢ\n"
                        + "fᵢ = a pᵢ Xᵢ / (1 + a h Σⱼ pⱼ Xⱼ)   (kills per wolf per year)\n"
                        + "dW/dt = e W Σ fᵢ − q W   (only when wolf numbers may change)\n"
                        + "Kᵢ is scaled by grass availability and by GP(t + ΔT) / GP(t), "
                        + "GP = exp[−0.5((t − 20)/var)²]."),
                text("Logistic growth (Gotelli, chapter 2) is the benchmark. Competition for grass (chapter 5) "
                        + "and the Lotka-Volterra predator-prey interaction with a saturating functional "
                        + "response (chapter 6, Predation) are the extensions. Time is in years."),
                Controls.heading("Limitations"),
                text("• All parameter values are illustrative assumptions. The herbivore values were "
                        + "chosen to follow the broad 1992–2017 pattern of the teaching data; the wolf values "
                        + "are not based on Oostvaardersplassen observations.\n"
                        + "• The predator/prey workbook is not used for calibration, because its North/South "
                        + "locations and data quality (“Err”, “NaN”, a 500 kg deer weight) have "
                        + "not been verified.\n"
                        + "• By default the number of wolves is held constant at W₀ to isolate its effect.\n"
                        + "• The temperature–grass link is the Appendix 3 growth potential, not a fitted "
                        + "local relationship. Drought, winter severity and other weather effects are not "
                        + "included. Flagged temperature scenarios are possible policy-review cases, not "
                        + "verified thresholds.\n"
                        + "• Geese, birds of prey, smaller animals, disease, age structure, migration and "
                        + "human measures such as culling are left out.\n"
                        + "• The model is deterministic: one run gives one trajectory, without random "
                        + "variation between years.\n"
                        + "• A model that fits a historical curve is not automatically a reliable ecological "
                        + "forecast."));
        box.setPadding(new Insets(16));
        box.setMaxWidth(820);
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        setContent(scroll);
    }

    private static Label text(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        return label;
    }
}
