package dev.smpcore.settings;

import org.bukkit.Material;

/**
 * Numeric value edited with a native slider.
 */
public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String unit;

    public NumberSetting(String path, Category category, Material icon, String name, String description,
                         double def, double min, double max, double step, String unit) {
        super(path, category, icon, name, description, def);
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit;
    }

    public double min() { return min; }
    public double max() { return max; }
    public double step() { return step; }
    public String unit() { return unit; }

    public boolean integral() {
        return step >= 1 && step == Math.floor(step);
    }

    @Override
    public Double get() {
        double v = config().getDouble(path(), def());
        return Math.max(min, Math.min(max, v));
    }

    public int asInt() {
        return (int) Math.round(get());
    }

    @Override
    protected Object toYaml(Double value) {
        return integral() ? (Object) (int) Math.round(value) : (Object) value;
    }

    public String format(double v) {
        String s = integral() ? String.valueOf((int) Math.round(v)) : String.format("%.2f", v);
        return unit.isEmpty() ? s : s + " " + unit;
    }

    @Override
    public String display() {
        return "<aqua>" + format(get()) + "</aqua>";
    }
}
