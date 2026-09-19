package dev.redstone.openpc.validation;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class PcValidationResult {

    private final List<Text> errors;

    private PcValidationResult(List<Text> errors) {
        this.errors = errors;
    }

    public static PcValidationResult ok() {
        return new PcValidationResult(List.of());
    }

    public static PcValidationResult failed(List<Text> errors) {
        return new PcValidationResult(List.copyOf(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<Text> errors() {
        return errors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Text> errors = new ArrayList<>();

        private Builder() {
        }

        public Builder error(Text message) {
            errors.add(message);
            return this;
        }

        public PcValidationResult build() {
            return errors.isEmpty() ? ok() : failed(errors);
        }
    }
}